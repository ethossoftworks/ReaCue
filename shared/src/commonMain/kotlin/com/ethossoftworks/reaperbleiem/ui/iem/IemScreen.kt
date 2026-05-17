package com.ethossoftworks.reaperbleiem.ui.iem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.systemui.KmpWindowInsets
import com.outsidesource.oskitcompose.systemui.vertical

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IemScreen(interactor: IemScreenViewInteractor = rememberInjectForRoute()) {
    val state = interactor.collectAsState()

    LaunchedEffect(Unit) { interactor.onMount() }

    Column(
        modifier =
            Modifier.windowInsetsPadding(KmpWindowInsets.vertical).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(state.projectName)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    value = state.tracks[state.selectedIemId]?.name ?: "Please Select",
                    onValueChange = {},
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                )

                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.tracks.forEach { (trackId, track) ->
                        if (!track.isIem) return@forEach
                        DropdownMenuItem(
                            text = { Text(text = state.tracks[trackId]?.name ?: return@DropdownMenuItem) },
                            onClick = {
                                interactor.onIemSelect(trackId)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (!state.isServiceRunning) {
                Button(onClick = interactor::onRestartClick) { Text(text = "Restart") }
            }

            Button(onClick = interactor::onRefreshClick) { Text(text = "Refresh") }
        }

        val track = state.tracks[state.selectedIemId] ?: return@Column
        val hardwareOut = track.hardwareOuts.values.firstOrNull()

        Column {
            Text("Output")
            Slider(value = hardwareOut?.volume ?: 0f, onValueChange = { interactor.onOutputVolumeChange(track.id, it) })
        }

        for (receive in track.receives) {
            Column {
                Text(state.tracks[receive.value.trackId]?.name ?: continue)
                Slider(
                    value = receive.value.volume,
                    onValueChange = { interactor.onReceiveVolumeChange(track.id, receive.key, it) },
                )
            }
        }
    }
}
