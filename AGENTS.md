# ReaCue Agent Guide

## Project Intent

ReaCue is a Kotlin Multiplatform app for personal IEM mixing with REAPER.

The macOS app is the host that should run near the REAPER machine. It connects to REAPER through the REAPER web interface and OSC, advertises a custom BLE service, and forwards mix state and commands. Android and iOS clients scan for that BLE host, connect, and control their personal monitor mix from mobile devices.

Keep changes aligned with that bridge model:

- REAPER is the source of track, send, hardware output, project, and OSC event state.
- macOS is the REAPER-to-BLE peripheral host.
- Android and iOS are BLE central clients.
- Shared Kotlin code owns almost all product behavior.

## Architecture Overview

- `shared` contains the cross-platform application: Compose UI, theme, routes, interactors, service contracts, Koin modules, BLE protocol models, and REAPER IEM service implementations.
- `androidApp` initializes Android Koin/lifecycle/capabilities, requests Bluetooth capability context, and hosts the shared `App()` composable.
- `iosApp` is the Xcode shell that loads `MainViewController()` from the shared iOS framework.
- `macOsApp` starts the native macOS Compose app, registers `macOsDiModule`, and acts as the BLE peripheral host for mobile clients.
- `reaperConfigs` contains the required REAPER OSC and Lua setup files.

Primary runtime classes:

- `AppCoordinator` selects the initial route: mobile platforms start at `Route.Scan`; desktop starts at `Route.Iem(IemContext.Peripheral)`.
- `IemInteractor` owns `IemState`, subscribes to `IIemService`, and applies incoming `IemEvent` updates to track/mix state.
- `NetworkIemService` talks directly to REAPER using the web interface and OSC UDP commands/events.
- `BlePeripheralIemService` runs on macOS, subscribes to `NetworkIemService`, advertises the custom BLE service, and sends CBOR `IemEvent` notifications to centrals.
- `BleCentralIemService` runs on mobile, scans for the ReaCue BLE service, connects to the host, reassembles notification packets, and writes CBOR command events back to the peripheral.

## Module And Source-Set Map

- Common app code lives under `shared/src/commonMain/kotlin/com/ethossoftworks/reaperbleiem`.
- Shared Compose resources live under `shared/src/commonMain/composeResources`.
- Android-specific Bluetooth and platform DI live under `shared/src/androidMain`.
- Apple-shared BLE implementations and interop live under `shared/src/appleMain`.
- iOS-specific DI/controller entrypoints live under `shared/src/iosMain`.
- macOS-specific DI lives under `shared/src/macosMain`; the native executable entrypoint lives in `macOsApp/src/macosArm64Main`.

Prefer `commonMain` for UI, state, interactors, routes, service contracts, protocol models, and behavior that can stay platform independent. Use platform source sets only for platform APIs, native Bluetooth implementations, app lifecycle/bootstrap, or dependency wiring that cannot be shared.

## Data Flow

REAPER-to-client state flow:

1. `NetworkIemService` reads track/send/hardware output state from REAPER web endpoints and listens for OSC notifications.
2. REAPER state is converted into `IemEvent` values and `Track`/`Mix` models.
3. On macOS, `BlePeripheralIemService` forwards those events over the event characteristic as CBOR payloads split into BLE notification packets.
4. On mobile, `BleCentralIemService` reassembles packets, decodes CBOR `IemEvent` values, and emits them to `IemInteractor`.
5. `IemInteractor` updates immutable state; screen interactors expose computed UI state to Compose screens.

Client-to-REAPER command flow:

1. UI controls call `IemScreenViewInteractor`.
2. The screen interactor calls `IemInteractor`.
3. Mobile clients write CBOR command events through `BleCentralIemService`; the macOS peripheral receives them in `BlePeripheralIemService`.
4. `BlePeripheralIemService` applies commands to `NetworkIemService`.
5. `NetworkIemService` sends OSC commands to REAPER and emits resulting updates back through the same state flow.

## Development Conventions

- Follow the existing Koin dependency graph in `DI.kt` and platform DI modules; add bindings near the source set that owns the implementation.
- Keep UI state in OSKit `Interactor` classes and keep Compose screens mostly declarative.
- Reuse existing theme, form components, resources, and string resources instead of creating one-off UI styles.
- Use immutable collection types where current state models use them, especially `PersistentMap` for track and mix state.
- Preserve the existing package structure and route/interactor/service layering.
- Let `IIemService` remain the boundary between UI/interactor state and transport details.
- Keep BLE transport changes compatible across central and peripheral implementations.
- Use ktfmt style configured by Gradle; do not hand-format against a different Kotlin style.

## Protocol And REAPER Cautions

Be careful with these compatibility points:

- BLE UUIDs in `BlePeripheralIemService` identify the ReaCue service, event characteristic, and command characteristic.
- `IemEvent` serialization uses kotlinx.serialization CBOR, `@SerialName`, and `@CborLabel`; changing labels or names is a protocol migration.
- BLE notifications use a 4-byte packet header: request id (`UInt16`) and packets remaining (`UInt16`).
- `NetworkIemService` assumes REAPER web access at `http://localhost:8000` by default, OSC command port `8000`, and OSC notification port `9000`.
- REAPER setup depends on `reaperConfigs/BleIem.ReaperOSC` and `reaperConfigs/BleIem.lua`.

If changing any protocol shape, update both central and peripheral paths together and include a migration note in the change summary.

## Verification And Build Commands

Use the smallest command set that covers the change:

- General verification: `./gradlew check`
- Static checks: `./gradlew detekt ktfmtCheck`
- Android debug build: `./gradlew :androidApp:assembleDebug`
- macOS run: `./gradlew :macOsApp:runDebugExecutableMacosArm64`
- macOS app bundle: `./gradlew :macOsApp:assembleApp`

Connected Android tests require a device or emulator. iOS validation requires Xcode and an appropriate simulator or device. For documentation-only changes, inspect the diff with `git diff -- AGENTS.md`; a Gradle run is usually unnecessary.

## Working Tree Notes

This repository may contain user changes. Do not revert unrelated work.
