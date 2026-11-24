package com.youtubeclone.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youtubeclone.app.player.PlayerController
import kotlin.math.abs
import kotlin.math.roundToInt

private fun snap(v: Float): Float = ((v.coerceIn(0.25f, 3f)) * 20).roundToInt() / 20f

/** ReVanced jaisa speed slider: 0.25x se 3x tak, 0.05 ke step mein + quick buttons. */
@Composable
fun SpeedDialog(onDismiss: () -> Unit) {
    val speed = PlayerController.speed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    speedLabel(speed),
                    Modifier.fillMaxWidth(),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { PlayerController.changeSpeed(snap(speed - 0.05f)) }) { Text("−", fontSize = 22.sp) }
                    Slider(
                        value = speed,
                        onValueChange = { PlayerController.changeSpeed(snap(it)) },
                        valueRange = 0.25f..3f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = YtRed,
                            activeTrackColor = YtRed,
                        ),
                    )
                    TextButton(onClick = { PlayerController.changeSpeed(snap(speed + 0.05f)) }) { Text("+", fontSize = 22.sp) }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                        YtChip(speedLabel(s), abs(s - speed) < 0.01f) { PlayerController.changeSpeed(s) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
