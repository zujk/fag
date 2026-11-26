package com.studiovoce.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Porzione della clip selezionata per il loop A-B, come frazioni 0..1. */
data class Selezione(val from: Float, val to: Float) {
    val valida: Boolean get() = to - from > 0.02f
}

/**
 * Forma d'onda con testina di riproduzione e selezione A-B: si trascina sulla forma
 * d'onda per scegliere il pezzo da ripetere in loop, si tocca per annullare (SPEC §5.3).
 *
 * @param peaks inviluppo a colonne, valori 0..1 (vedi `Waveform.peaks`)
 * @param position posizione di riproduzione come frazione 0..1, o null
 */
@Composable
fun WaveformView(
    peaks: FloatArray?,
    modifier: Modifier = Modifier,
    position: Float? = null,
    selezione: Selezione? = null,
    onSelezione: ((Selezione?) -> Unit)? = null,
    altezza: Dp = 96.dp,
) {
    val colors = MaterialTheme.colorScheme
    var dragStart by remember { mutableFloatStateOf(0f) }
    var larghezza by remember { mutableFloatStateOf(1f) }

    val descrizione = when {
        peaks == null -> "Forma d'onda in caricamento"
        selezione != null && selezione.valida ->
            "Forma d'onda, pezzo selezionato dal ${(selezione.from * 100).toInt()}" +
                " al ${(selezione.to * 100).toInt()} per cento"
        else -> "Forma d'onda della clip"
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(altezza)
            .semantics { contentDescription = descrizione }
            .then(
                if (onSelezione == null) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onSelezione(null) })
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                larghezza = size.width.toFloat().coerceAtLeast(1f)
                                dragStart = (offset.x / larghezza).coerceIn(0f, 1f)
                                onSelezione(null)
                            },
                            onHorizontalDrag = { change, _ ->
                                val now = (change.position.x / larghezza).coerceIn(0f, 1f)
                                onSelezione(
                                    Selezione(minOf(dragStart, now), maxOf(dragStart, now)),
                                )
                            },
                        )
                    },
            ),
    ) {
        Canvas(Modifier.fillMaxWidth().height(altezza)) {
            val w = size.width
            val h = size.height
            val mid = h / 2f

            // Linea di mezzo, così una clip quasi muta si vede comunque.
            drawLine(
                color = colors.outlineVariant,
                start = Offset(0f, mid),
                end = Offset(w, mid),
                strokeWidth = 1f,
            )

            if (selezione != null && selezione.valida) {
                drawRect(
                    color = colors.primary.copy(alpha = 0.18f),
                    topLeft = Offset(selezione.from * w, 0f),
                    size = Size((selezione.to - selezione.from) * w, h),
                )
            }

            if (peaks != null && peaks.isNotEmpty()) {
                val step = w / peaks.size
                val barWidth = (step * 0.7f).coerceAtLeast(1f)
                for (i in peaks.indices) {
                    val x = i * step + step / 2f
                    val amp = (peaks[i].coerceIn(0f, 1f) * (h / 2f - 2f)).coerceAtLeast(1f)
                    val dentro = selezione == null || !selezione.valida ||
                        (i.toFloat() / peaks.size in selezione.from..selezione.to)
                    drawLine(
                        color = if (dentro) colors.primary else colors.primary.copy(alpha = 0.35f),
                        start = Offset(x, mid - amp),
                        end = Offset(x, mid + amp),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }

            if (position != null) {
                val x = position.coerceIn(0f, 1f) * w
                drawLine(
                    color = colors.secondary,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 3f,
                )
            }
        }
    }
}

/** Due curve a confronto: nella v0 serve solo a mostrare clip e ripetizione affiancate. */
@Composable
fun WaveformConfronto(
    peaksClip: FloatArray?,
    peaksRipetizione: FloatArray?,
    modifier: Modifier = Modifier,
    coloreRipetizione: Color = MaterialTheme.colorScheme.secondary,
) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxWidth().height(128.dp)) {
        val h = size.height
        val w = size.width
        fun disegna(peaks: FloatArray?, mid: Float, colore: Color) {
            if (peaks == null || peaks.isEmpty()) return
            val step = w / peaks.size
            for (i in peaks.indices) {
                val amp = (peaks[i].coerceIn(0f, 1f) * (h / 4f - 2f)).coerceAtLeast(1f)
                drawLine(
                    color = colore,
                    start = Offset(i * step + step / 2f, mid - amp),
                    end = Offset(i * step + step / 2f, mid + amp),
                    strokeWidth = (step * 0.7f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }
        }
        disegna(peaksClip, h / 4f, colors.primary)
        disegna(peaksRipetizione, h * 3f / 4f, coloreRipetizione)
    }
}
