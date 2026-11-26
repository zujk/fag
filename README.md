# Studio Fagnani — pacchetto di partenza per Claude Code

Contenuto:
- `SPEC.md` — specifica completa dell'app
- `CLAUDE.md` — istruzioni operative per Claude Code
- `seed/clips.json` — manifest delle 40 clip della voice bank
- `tools/build_manifest.py` — rigenera manifest e copia gli audio dal foglio

Avvio:
1. Crea una cartella per il progetto e scompatta qui dentro questo pacchetto.
2. Copia gli audio: `python tools/build_manifest.py C:\DEV\Claude\fff --out app/src/main/assets/clips`
3. Apri Claude Code nella cartella e scrivi: "Leggi CLAUDE.md e SPEC.md e inizia la milestone v0."

---

## Stato: milestone v1

**Compila e i 179 test passano** (CI di GitHub, run #15: 0 errori di compilazione,
0 fallimenti, APK di debug prodotto).

Fatto nella v0 (SPEC §10):

- **Libreria** con le 40 clip, filtri per stato, categoria, contesto, energia e Livello.
  Riproduzione, velocità 0,75x e loop A-B trascinando sulla forma d'onda.
- **Esercizio su clip** (SPEC §5.3): ascolta 3 volte con pausa di 1 s → beep e
  registrazione che si ferma da sola dopo 1,5 s di silenzio → clip e ripetizione in fila.
- **Ripasso a intervalli** (SPEC §5.4) come funzione pura, con test su date finte.

Fatto nella v1 (SPEC §10):

- **Curve di altezza** con YIN: finestra 40 ms, passo 10 ms, 75-500 Hz. Sui seni a 100,
  180 e 250 Hz l'errore misurato è fra lo 0,000% e lo 0,005%, contro l'1% richiesto;
  sul glissando 150→250 Hz la curva è monotona.
- **Confronto** delle due curve in semitoni, allineate con DTW, e **riscontro in parole**
  con le sei regole di §5.5: al massimo due frasi, nessun punteggio.
- **Sessione guidata** con un tasto solo: riscaldamento, blocco della fase, ripasso e
  "Com'è la voce adesso?" in chiusura. Due "Affaticata" di fila e il giorno dopo la
  sessione è solo riscaldamento leggero.
- **Fase 0** con le tre registrazioni guidate e la scheda bersaglio.
- **Impostazioni**: fase corrente (l'app la suggerisce, la confermi tu), primo giorno,
  brano fisso, cancellazione delle registrazioni.
- Esercizi e domande sono **dati**, in `assets/exercises.json`, come le clip.
- Tutto offline: nessun permesso INTERNET nel manifest finale (viene anche rimosso con
  `tools:node="remove"` nel caso una libreria provi a unirlo; il log della CI conferma
  che nessuna lo chiede).

**Quello che i test non coprono.** Le due parti di interfaccia della v1 — la sessione
guidata e la fase 0 — compilano e non sono mai state eseguite: non c'è un emulatore in
questo ambiente. I criteri misurabili (YIN, regole di riscontro, pianificatore) sono
verificati dai test unitari; il resto va provato sul telefono.

Rimandato alle milestone successive, come da SPEC §10: modalità macchina e treno (v2);
formanti LPC, controllo della domenica con i grafici dei progressi,
aggiungi/importa/esporta clip e backup zip (v3). Le colonne `f1_med` e `f2_med` esistono
già e restano vuote finché non arrivano le LPC.

### Clip audio

I 40 WAV stanno nel repo, in `app/src/main/assets/clips/`, così l'APK che costruisce la
CI arriva sul telefono già con l'audio. Sono spezzoni brevi di trasmissioni televisive,
tenuti qui come citazione per studio personale. Si rigenerano dal foglio con:

```
python tools/build_manifest.py C:\DEV\Claudeff --out app/src/main/assets/clips
```

Una clip con `audio_present: false` o con il file mancante si importa ma resta nascosta, e
viene contata in un avviso in Libreria e in Oggi: è il percorso che si vede quando gli
asset non sono stati rigenerati.

### L'APK da installare

Ogni build riuscita pubblica l'APK di debug come release con il tag `apk-ultima`.
Il link è sempre lo stesso e si apre direttamente dal telefono:

```
https://github.com/zujk/fag/releases/download/apk-ultima/studio-fagnani.apk
```

È firmato con la chiave di debug, quindi al primo avvio Android chiede di consentire
l'installazione da questa origine. Pesa 73 MB: 37,6 di clip audio e 34,6 di codice
(un APK di debug non passa da R8, quindi il dex non è ridotto).

Lo stesso file sta anche fra gli artefatti della run, ma lì arriva dentro uno zip e
serve l'accesso al repo: scomodo da aprire su Android.

### Come compilare

Serve **JDK 17** e l'**Android SDK** (API 35). Sulla macchina di sviluppo non ci sono,
quindi a compilare ci pensa la CI di GitHub (`.github/workflows/android.yml`): usa il
`gradle` e l'SDK già presenti sul runner, quindi non serve tenere `gradle-wrapper.jar`
(un binario) nel repo.

L'esito si legge in due modi. Come artefatti della run (rapporti dei test e APK), che
però si scaricano solo con l'accesso al repo; oppure sul ramo **`ci-rapporto`**, che la
CI riscrive a ogni giro con `RAPPORTO.md` (errori di compilazione e test falliti),
`gradle.log` per intero e gli XML dei test:

```
https://raw.githubusercontent.com/zujk/fag/ci-rapporto/RAPPORTO.md
```

Attenzione: `raw.githubusercontent.com` tiene il file in cache per qualche minuto,
quindi subito dopo una run mostra ancora il rapporto di quella prima. Per leggere
l'ultimo senza aspettare si passa dall'API dei contenuti, che non ha cache:

```
https://api.github.com/repos/zujk/fag/contents/RAPPORTO.md?ref=ci-rapporto
```

In locale, quando ci sarà un JDK:

1. Installa Android Studio (porta JDK e SDK) oppure JDK 17 + command-line tools.
2. Scrivi `local.properties` con `sdk.dir=C\:\\percorso\\Android\\Sdk`.
3. Genera il wrapper con `gradle wrapper --gradle-version 8.11.1`, oppure apri la
   cartella in Android Studio, che lo crea da sé.
4. Poi, come dice `CLAUDE.md`:

```
./gradlew test
./gradlew assembleDebug
```

### Lo schema del database

`app/schemas/` tiene lo schema di Room esportato, versione per versione. Serve a
scrivere le migrazioni guardando com'era la tabella e non a memoria: progressi e
registrazioni non si possono perdere (SPEC §4.1), quindi non c'è
`fallbackToDestructiveMigration`.

Lo genera il plugin `androidx.room` durante la build. In locale, senza JDK, si
recupera dal ramo `ci-rapporto`, dove la CI lo pubblica insieme al rapporto.

### Test (JVM, senza emulatore)

| File | Cosa copre |
| --- | --- |
| `dsp/YinTest` | i criteri di §10 sui seni e sul glissando, errore di ottava, silenzio e rumore, altezze fuori intervallo, sottocampionamento e passa-basso |
| `dsp/ContourTest` | semitoni sulla mediana, percentili, escursione, durata del solo parlato, pendenza della chiusura, codifica di `contour_blob` |
| `dsp/DtwTest` | allineamento di curve identiche, dilatate e spostate, frame muti con penale fissa, media sui frame che cadono insieme |
| `session/FeedbackRulesTest` | le sei regole di §5.5 una per una, priorità e limite di due frasi, soglie dalla configurazione |
| `session/SessionPlannerTest` | le cinque fasi, sovrapposizione 1-2, voce affaticata, tempi del riscaldamento, rotazione delle categorie, fase suggerita |
| `session/SpacedRepetitionTest` | intervalli per Livello, limite di 6 clip, precedenza della categoria della settimana, date finte |
| `data/ClipAnalyzerTest` | misure di §4.4 su segnali sintetici, cosa il foglio sovrascrive e cosa no |
| `data/ExerciseMergeTest` | righe malformate scartate, il file vero degli asset, le domande della fase 0 |
| `data/ManifestMergeTest` | l'import non tocca mai i progressi, clip senza audio nascoste, manifest vero con 40 clip |
| `data/SettingsStoreTest` | codifica dell'ordine delle categorie |
| `audio/SilenceDetectorTest` | stop dopo 1,5 s di silenzio, pause brevi che non fermano, tetto di durata, taratura del rumore |
| `dsp/WavIoTest` | scrittura/rilettura di segnali sintetici, writer incrementale, file troncati, stereo→mono |
| `dsp/WaveformTest` | inviluppo, RMS su float e su PCM 16 bit, casi limite |
