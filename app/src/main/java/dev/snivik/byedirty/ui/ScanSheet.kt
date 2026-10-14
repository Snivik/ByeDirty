package dev.snivik.byedirty.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.snivik.byedirty.R
import dev.snivik.byedirty.ui.theme.FilterGood
import dev.snivik.byedirty.ui.theme.FilterReplace
import kotlinx.coroutines.delay

/** Android's take on the iOS "Ready to Scan" sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(state: ScanState, actions: AppActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (state is ScanState.Success) {
        LaunchedEffect(Unit) {
            delay(1200)
            actions.dismissScan()
        }
    }
    ModalBottomSheet(
        onDismissRequest = actions.dismissScan,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is ScanState.Waiting -> {
                    PulsingIcon()
                    Title("Ready to scan")
                    Body(
                        if (state.fullMemory) "Hold the back of your phone against the shower head handle and keep it still. A full scan takes a few seconds."
                        else "Hold the back of your phone against the shower head handle."
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(actions.dismissScan) { Text("Cancel") }
                }
                is ScanState.Reading -> {
                    StatusIcon(R.drawable.ic_nfc, MaterialTheme.colorScheme.secondary)
                    Title("Reading…")
                    Body("Keep holding your phone still.")
                    Spacer(Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(48.dp))
                }
                is ScanState.Success -> {
                    StatusIcon(R.drawable.ic_check_circle, FilterGood)
                    Title("Synced")
                    Body(state.message)
                    Spacer(Modifier.height(48.dp))
                }
                is ScanState.Error -> {
                    StatusIcon(R.drawable.ic_error, FilterReplace)
                    Title(if (state.nfcOff) "NFC is off" else "Couldn't read the shower head")
                    Body(state.message)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(actions.dismissScan) { Text("Close") }
                        if (state.nfcOff) Button(actions.openNfcSettings) { Text("Open settings") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
}

@Composable
private fun Body(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StatusIcon(icon: Int, tint: androidx.compose.ui.graphics.Color) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.16f), modifier = Modifier.size(88.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(44.dp), tint = tint)
        }
    }
}

@Composable
private fun PulsingIcon() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale",
    )
    Box(Modifier.scale(scale)) { StatusIcon(R.drawable.ic_drop, MaterialTheme.colorScheme.secondary) }
}
