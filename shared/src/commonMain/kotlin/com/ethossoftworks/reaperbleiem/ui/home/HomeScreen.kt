package com.ethossoftworks.reaperbleiem.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    interactor: HomeScreenViewInteractor = rememberInjectForRoute(),
) {
    val state = interactor.collectAsState()

    LaunchedEffect(Unit) {
        interactor.onMounted()
    }

    Column {
        Text(text = "Bluetooth: ${state.bluetoothStatus}")
        Text(text = "Running: ${state.isServerRunning}")

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                readOnly = true,
                value = "",
                onValueChange = {},
                label = { Text("") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                state.iemOptions.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(text = selectionOption.name) },
                        onClick = {
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}