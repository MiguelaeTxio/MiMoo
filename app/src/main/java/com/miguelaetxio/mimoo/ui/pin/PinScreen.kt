package com.miguelaetxio.mimoo.ui.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Pantalla de PIN de acceso (H07 PARTE 2, PASO 2.7). Se muestra desde
 * MainActivity ANTES que cualquier otra cosa (drawer, NavGraph,
 * escaneo inicial) mientras `PinViewModel.isUnlocked` sea `false` --
 * ver MainActivity.kt para el punto de enganche exacto.
 *
 * Texto del campo literal por decisión explícita de Miguel Ángel,
 * igual en ambos dispositivos: "Introduce tu PIN, Silvia".
 * ---
 * Access PIN screen (H07 PART 2, STEP 2.7). Shown from MainActivity
 * BEFORE anything else (drawer, NavGraph, initial scan) while
 * `PinViewModel.isUnlocked` is `false` -- see MainActivity.kt for the
 * exact hook point.
 *
 * Literal field text by Miguel Ángel's explicit decision, identical
 * on both devices: "Introduce tu PIN, Silvia".
 */
@Composable
fun PinScreen(
    viewModel: PinViewModel = hiltViewModel(),
) {
    val showError by viewModel.showError.collectAsState()
    var pinInput by rememberSaveable { mutableStateOf("") }

    // Limpia el campo y el error cada vez que se muestra un fallo
    // nuevo, para que el siguiente intento empiece en blanco.
    // ---
    // Clears the field and the error each time a new failure shows,
    // so the next attempt starts blank.
    LaunchedEffect(showError) {
        if (showError) {
            pinInput = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Introduce tu PIN, Silvia",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { newValue ->
                    if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                        pinInput = newValue
                        viewModel.dismissError()
                        if (newValue.length == 4) {
                            viewModel.submitPin(newValue)
                        }
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.submitPin(pinInput) },
                ),
                isError = showError,
                singleLine = true,
            )
            if (showError) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "PIN incorrecto",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
