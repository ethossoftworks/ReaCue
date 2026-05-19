@file:OptIn(ExperimentalForeignApi::class, InternalComposeUiApi::class)

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.DefaultArchitectureComponentsOwner
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import com.outsidesource.oskitcompose.geometry.toOffset
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import platform.AppKit.NSBackingStoreBuffered
import platform.AppKit.NSCursor
import platform.AppKit.NSEvent
import platform.AppKit.NSEventModifierFlagCommand
import platform.AppKit.NSEventModifierFlagControl
import platform.AppKit.NSEventModifierFlagOption
import platform.AppKit.NSEventModifierFlagShift
import platform.AppKit.NSKeyDown
import platform.AppKit.NSKeyUp
import platform.AppKit.NSTrackingActiveAlways
import platform.AppKit.NSTrackingActiveInKeyWindow
import platform.AppKit.NSTrackingArea
import platform.AppKit.NSTrackingAssumeInside
import platform.AppKit.NSTrackingInVisibleRect
import platform.AppKit.NSTrackingMouseEnteredAndExited
import platform.AppKit.NSTrackingMouseMoved
import platform.AppKit.NSView
import platform.AppKit.NSApplication
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowDelegateProtocol
import platform.AppKit.NSWindowDidChangeBackingPropertiesNotification
import platform.Foundation.NSNotification
import platform.darwin.NSObject
import platform.AppKit.NSWindowStyleMaskClosable
import platform.AppKit.NSWindowStyleMaskFullSizeContentView
import platform.AppKit.NSWindowStyleMaskMiniaturizable
import platform.AppKit.NSWindowStyleMaskResizable
import platform.AppKit.NSWindowStyleMaskTitled
import platform.AppKit.NSWindowTitleHidden
import platform.Foundation.NSMakeRect
import platform.Foundation.NSMakeSize
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import org.jetbrains.skia.Canvas as SkiaCanvas

/**
 * Replaces the Compose-provided Window composable with a version that fixes two bugs present
 * in Compose Multiplatform 1.11.0:
 *
 * 1. Title bar flash on launch: ComposeWindow calls makeKeyAndOrderFront before setContent,
 *    so the window is visible before any composition runs. We configure the chrome flags
 *    (FullSizeContentView, titleVisibility, titlebarAppearsTransparent) before the window
 *    is ordered front so they take effect on the very first frame.
 *
 * 2. Hit-box drift after monitor change: ComposeWindow sets scene.density once from
 *    backingScaleFactor at init time and never updates it. Mouse event coordinates are
 *    converted via event.offset.toOffset(scene.density), so a stale density causes every
 *    click to land at the wrong logical pixel after moving to a display with different DPI.
 *    We observe NSWindowDidChangeBackingPropertiesNotification and update scene.density,
 *    which also cascades to RootNodeOwner (and therefore LocalDensity) through the
 *    CanvasLayersComposeScene setter.
 *
 * Trade-off vs the stock implementation: MacosTextInputService (internal) is omitted, so
 * IME composition (e.g. Chinese/Japanese input) is not supported. Basic typing still works
 * via keyDown/keyUp key events.
 */
fun CustomWindow(
    title: String = "ComposeWindow",
    size: DpSize = DpSize(800.dp, 600.dp),
    minSize: DpSize = DpSize(400.dp, 300.dp),
    content: @Composable WindowScope.() -> Unit,
) {
    AppWindow(title = title, size = size, minSize = minSize, content = content)
}

private class AppWindow(
    title: String,
    size: DpSize,
    minSize: DpSize,
    content: @Composable WindowScope.() -> Unit,
) : WindowScope {
    private val archComponentsOwner = DefaultArchitectureComponentsOwner()
    private val skiaLayer = SkiaLayer()
    private val windowInfo = object : WindowInfo {
        override var isWindowFocused: Boolean = true
        override var containerSize: IntSize = IntSize.Zero
        override var keyboardModifiers: PointerKeyboardModifiers = PointerKeyboardModifiers(0)
    }
    private val scene = CanvasLayersComposeScene(
        coroutineContext = Dispatchers.Main,
        platformContext = object : PlatformContext by PlatformContext.Empty() {
            override val architectureComponentsOwner get() = archComponentsOwner
            override val windowInfo: WindowInfo get() = this@AppWindow.windowInfo
            override fun setPointerIcon(pointerIcon: PointerIcon) {
                NSCursor.arrowCursor.set()
            }
        },
        invalidate = skiaLayer::needRender,
    )
    private val renderDelegate = SkikoRenderDelegate { canvas, width, height, nanoTime ->
        val newSize = IntSize(width, height)
        windowInfo.containerSize = newSize
        scene.size = newSize
        scene.render(canvas.asComposeCanvas(), nanoTime)
    }

    override val window = object : NSWindow(
        contentRect = NSMakeRect(0.0, 0.0, size.width.value.toDouble(), size.height.value.toDouble()),
        styleMask = NSWindowStyleMaskTitled or
            NSWindowStyleMaskMiniaturizable or
            NSWindowStyleMaskClosable or
            NSWindowStyleMaskResizable,
        backing = NSBackingStoreBuffered,
        defer = true,
    ) {
        override fun canBecomeKeyWindow() = true
        override fun canBecomeMainWindow() = true
    }

    private val view = object : NSView(window.frame) {
        private var trackingArea: NSTrackingArea? = null

        override fun wantsUpdateLayer() = true
        override fun acceptsFirstResponder() = true

        override fun viewWillMoveToWindow(newWindow: NSWindow?) {
            updateTrackingAreas()
        }

        override fun updateTrackingAreas() {
            trackingArea?.let { removeTrackingArea(it) }
            trackingArea = NSTrackingArea(
                rect = bounds,
                options = NSTrackingActiveAlways or
                    NSTrackingMouseEnteredAndExited or
                    NSTrackingMouseMoved or
                    NSTrackingActiveInKeyWindow or
                    NSTrackingAssumeInside or
                    NSTrackingInVisibleRect,
                owner = this,
                userInfo = null,
            )
            addTrackingArea(trackingArea!!)
        }

        override fun mouseDown(event: NSEvent) =
            onMouseEvent(event, PointerEventType.Press, PointerButton.Primary)
        override fun mouseUp(event: NSEvent) =
            onMouseEvent(event, PointerEventType.Release, PointerButton.Primary)
        override fun rightMouseDown(event: NSEvent) =
            onMouseEvent(event, PointerEventType.Press, PointerButton.Secondary)
        override fun rightMouseUp(event: NSEvent) =
            onMouseEvent(event, PointerEventType.Release, PointerButton.Secondary)
        override fun otherMouseDown(event: NSEvent) =
            onMouseEvent(event, PointerEventType.Press, PointerButton(event.buttonNumber.toInt()))
        override fun otherMouseUp(event: NSEvent) =
            onMouseEvent(event, PointerEventType.Release, PointerButton(event.buttonNumber.toInt()))
        override fun mouseMoved(event: NSEvent) = onMouseEvent(event, PointerEventType.Move)
        override fun mouseDragged(event: NSEvent) = onMouseEvent(event, PointerEventType.Move)
        override fun scrollWheel(event: NSEvent) = onMouseEvent(event, PointerEventType.Scroll)

        override fun keyDown(event: NSEvent) {
            if (!scene.sendKeyEvent(event.toComposeKeyEvent())) super.keyDown(event)
        }

        override fun keyUp(event: NSEvent) {
            scene.sendKeyEvent(event.toComposeKeyEvent())
        }
    }

    private val delegate = object : NSObject(), NSWindowDelegateProtocol {
        override fun windowWillClose(notification: NSNotification) {
            NSApplication.sharedApplication().terminate(null)
        }
    }

    init {
        window.title = title
        window.delegate = delegate
        window.contentView = view
        skiaLayer.renderDelegate = renderDelegate
        skiaLayer.attachTo(view)

        window.styleMask = window.styleMask or NSWindowStyleMaskFullSizeContentView
        window.titleVisibility = NSWindowTitleHidden
        window.titlebarAppearsTransparent = true
        window.minSize = NSMakeSize(minSize.width.value.toDouble(), minSize.height.value.toDouble())

        // Restore saved frame; center only on first launch when no saved frame exists.
        val hasRestoredFrame = window.setFrameAutosaveName(title)
        if (!hasRestoredFrame) window.center()
        window.makeKeyAndOrderFront(null)

        scene.density = Density(window.backingScaleFactor.toFloat())

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSWindowDidChangeBackingPropertiesNotification,
            `object` = window,
            queue = NSOperationQueue.mainQueue,
        ) {
            scene.density = Density(window.backingScaleFactor.toFloat())
        }

        scene.setContent { content() }
        archComponentsOwner.enableSavedStateHandles()
        archComponentsOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun onMouseEvent(
        event: NSEvent,
        eventType: PointerEventType,
        button: PointerButton? = null,
    ) {
        scene.sendPointerEvent(
            eventType = eventType,
            position = event.logicalOffset.toOffset(scene.density),
            scrollDelta = Offset(x = event.deltaX.toFloat(), y = event.deltaY.toFloat()),
            nativeEvent = event,
            button = button,
        )
    }

    private val NSEvent.logicalOffset: DpOffset
        get() {
            val position = locationInWindow.useContents { DpOffset(x = x.dp, y = y.dp) }
            val height = view.frame.useContents { size.height.dp }
            return DpOffset(x = position.x, y = height - position.y)
        }

    private fun NSEvent.toComposeKeyEvent() = KeyEvent(
        key = Key(keyCode.toLong()),
        type = when (type) {
            NSKeyDown -> KeyEventType.KeyDown
            NSKeyUp -> KeyEventType.KeyUp
            else -> KeyEventType.Unknown
        },
        codePoint = characters?.firstOrNull()?.code ?: 0,
        isAltPressed = modifierFlags and NSEventModifierFlagOption != 0UL,
        isShiftPressed = modifierFlags and NSEventModifierFlagShift != 0UL,
        isCtrlPressed = modifierFlags and NSEventModifierFlagControl != 0UL,
        isMetaPressed = modifierFlags and NSEventModifierFlagCommand != 0UL,
        nativeEvent = this,
    )
}
