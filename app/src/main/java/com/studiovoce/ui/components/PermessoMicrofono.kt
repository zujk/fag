package com.studiovoce.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Il permesso del microfono, in un posto solo.
 *
 * Dichiararlo nel manifest non basta da Android 6: va chiesto a runtime, e senza
 * il permesso `AudioRecord` nasce non inizializzato — cioè la registrazione
 * fallisce con un messaggio tecnico e nessuna finestra di sistema in vista.
 *
 * Sta qui e non dentro una schermata perché i punti in cui si registra sono più
 * d'uno (l'esercizio su clip, le registrazioni guidate della fase 0, e domani la
 * modalità macchina): ognuno che se lo riscriva è un'occasione per dimenticarlo.
 */
class PermessoMicrofono internal constructor(
    /** Vero se si può registrare adesso. */
    val concesso: Boolean,
    /** Vero quando Ziv ha detto no e Android non mostrerà più la finestra. */
    val negato: Boolean,
    /** Chiede il permesso, oppure registra subito se c'è già. */
    val chiediEProcedi: () -> Unit,
    private val context: Context,
) {

    /** Apre la pagina dell'app nelle impostazioni di sistema. */
    fun apriImpostazioni() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * @param onConcesso cosa fare appena il permesso c'è: di solito avviare la
 *   registrazione, così il tasto non va premuto due volte.
 */
@Composable
fun rememberPermessoMicrofono(onConcesso: () -> Unit): PermessoMicrofono {
    val context = LocalContext.current

    var concesso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var negato by remember { mutableStateOf(false) }

    val richiesta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        concesso = ok
        negato = !ok
        if (ok) onConcesso()
    }

    return PermessoMicrofono(
        concesso = concesso,
        negato = negato,
        chiediEProcedi = {
            if (concesso) onConcesso()
            else richiesta.launch(Manifest.permission.RECORD_AUDIO)
        },
        context = context,
    )
}

/** L'avviso da mostrare quando il permesso è stato negato. */
@Composable
fun AvvisoMicrofonoNegato(permesso: PermessoMicrofono, modifier: Modifier = Modifier) {
    if (!permesso.negato) return
    Avviso(
        "Senza il microfono non si può registrare la tua voce. " +
            "Il permesso si dà dalle impostazioni del telefono.",
        modifier,
    ) {
        TextButton(onClick = permesso::apriImpostazioni) { Text("Apri le impostazioni") }
    }
}
