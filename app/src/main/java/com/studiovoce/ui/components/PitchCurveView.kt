package com.studiovoce.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.studiovoce.ui.theme.Spazi
import kotlin.math.abs
import kotlin.math.max

/**
 * Le curve di altezza (SPEC §5.3).
 *
 * I valori sono in semitoni rispetto alla mediana di ciascuna curva, quindi lo
 * zero e' "l'altezza media di chi parla" e non una nota: due voci diverse si
 * possono guardare sugli stessi assi. `NaN` significa "qui non c'e' voce", e la
 * linea si interrompe invece di scendere a zero: un buco e' un buco.
 */

/** Scala verticale minima, in semitoni: sotto, una curva piatta sembrerebbe mossa. */
private const val SEMITONI_MINIMI = 6f

/** Curva sola, da mettere in sovrimpressione sulla forma d'onda. */
@Composable
fun CurvaAltezza(
    curva: FloatArray?,
    modifier: Modifier = Modifier,
    colore: Color = MaterialTheme.colorScheme.primary,
    altezza: Dp = 96.dp,
    descrizione: String = "Curva dell'altezza della voce",
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(altezza)
            .semantics { contentDescription = descrizione },
    ) {
        Canvas(Modifier.fillMaxWidth().height(altezza)) {
            val scala = scalaDi(curva)
            disegnaCurva(curva, scala, colore, spessore = 3f)
        }
    }
}

/**
 * Le due curve sovrapposte dopo una ripetizione (SPEC §5.3 punto 3).
 *
 * [tua] deve essere gia' riportata sui tempi di [sua] con DTW: qui si disegna, non
 * si allinea. Le due curve condividono la scala verticale, altrimenti un confronto
 * di forme mostrerebbe due forme riscalate in modo diverso.
 */
@Composable
fun CurveSovrapposte(
    sua: FloatArray?,
    tua: FloatArray?,
    modifier: Modifier = Modifier,
    altezza: Dp = 160.dp,
    coloreSua: Color = MaterialTheme.colorScheme.primary,
    coloreTua: Color = MaterialTheme.colorScheme.tertiary,
) {
    val colors = MaterialTheme.colorScheme
    val vuote = contaSonori(sua) < 2 && contaSonori(tua) < 2

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(altezza)
                .semantics {
                    contentDescription = if (vuote) {
                        "Curve non disponibili"
                    } else {
                        "Le due curve dell'altezza, la clip e la tua ripetizione, " +
                            "allineate nel tempo"
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(altezza)) {
                val scala = max(scalaDi(sua), scalaDi(tua))

                // Lo zero: l'altezza media di ciascuna voce. Serve da riferimento
                // per capire se una frase sale o scende.
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )

                disegnaCurva(sua, scala, coloreSua, spessore = 4f)
                disegnaCurva(tua, scala, coloreTua, spessore = 4f)
            }
        }

        if (vuote) {
            StatoVuoto("Niente da confrontare: non ho sentito abbastanza voce.")
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = Spazi.s),
                horizontalArrangement = Arrangement.spacedBy(Spazi.m),
            ) {
                VoceLegenda("Fagnani", coloreSua)
                VoceLegenda("Tu", coloreTua)
            }
        }
    }
}

@Composable
private fun VoceLegenda(testo: String, colore: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(colore))
        Text(
            testo,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = Spazi.xs),
        )
    }
}

/**
 * Mezza ampiezza della scala verticale, in semitoni: il massimo scostamento dalla
 * mediana, mai sotto [SEMITONI_MINIMI].
 */
private fun scalaDi(curva: FloatArray?): Float {
    if (curva == null) return SEMITONI_MINIMI
    var massimo = 0f
    for (v in curva) if (!v.isNaN()) massimo = max(massimo, abs(v))
    return max(massimo, SEMITONI_MINIMI)
}

private fun contaSonori(curva: FloatArray?): Int =
    curva?.count { !it.isNaN() } ?: 0

/**
 * Disegna la curva come una serie di tratti, uno per ogni pezzo di voce continua.
 * Dove la voce manca il tratto finisce e ne comincia un altro dopo il buco: una
 * linea che attraversa il silenzio direbbe che li' c'e' un'altezza, e non c'e'.
 */
private fun DrawScope.disegnaCurva(
    curva: FloatArray?,
    scalaSemitoni: Float,
    colore: Color,
    spessore: Float,
) {
    if (curva == null || curva.isEmpty()) return
    val h = size.height
    val passo = if (curva.size > 1) size.width / (curva.size - 1) else size.width

    fun y(semitoni: Float): Float =
        (h / 2f - semitoni / scalaSemitoni * (h / 2f - spessore)).coerceIn(0f, h)

    var i = 0
    while (i < curva.size) {
        if (curva[i].isNaN()) {
            i++
            continue
        }
        // Fine del tratto di voce continua che comincia qui.
        var fine = i
        while (fine + 1 < curva.size && !curva[fine + 1].isNaN()) fine++

        if (fine == i) {
            // Un solo frame sonoro fra due silenzi: un punto, non una linea.
            drawCircle(colore, spessore / 1.5f, Offset(i * passo, y(curva[i])))
        } else {
            val path = Path()
            path.moveTo(i * passo, y(curva[i]))
            for (k in i + 1..fine) path.lineTo(k * passo, y(curva[k]))
            drawPath(path, colore, style = Stroke(width = spessore, cap = StrokeCap.Round))
        }
        i = fine + 1
    }
}
