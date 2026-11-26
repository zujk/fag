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

## Stato: milestone v0

Stato: **compila e i 50 test passano** (CI di GitHub, run #4: 0 errori di
compilazione, 0 fallimenti, APK di debug prodotto).

Fatto (SPEC §10, v0):

- **Libreria** con le 40 clip, filtri per stato, categoria, contesto, energia e Livello.
  Riproduzione, velocità 0,75x e loop A-B trascinando sulla forma d'onda.
- **Esercizio su clip** (SPEC §5.3, parte v0): ascolta 3 volte con pausa di 1 s → beep e
  registrazione che si ferma da sola dopo 1,5 s di silenzio → clip e ripetizione in fila,
  con le due forme d'onda → voto 1-5 → "Ancora" / "Prossima".
- **Ripasso a intervalli** (SPEC §5.4) come funzione pura, con test su date finte.
- **Oggi**: un tasto grande che apre il ripasso del giorno.
- Tutto offline: nessun permesso INTERNET nel manifest finale (viene anche rimosso con
  `tools:node="remove"` nel caso una libreria provi a unirlo).

Rimandato alle milestone successive, come da SPEC §10: curve di altezza con YIN e
confronto DTW, riscontro in parole, percorso guidato con fasi e domanda sulla fatica (v1);
modalità macchina e treno (v2); formanti, controllo della domenica, aggiungi/importa/esporta
clip e backup zip (v3). `assets/exercises.json` arriva con la v1, insieme al riscaldamento.

### Clip audio — non stanno nel repo

Nel repo ci sono **solo i metadati** (`app/src/main/assets/clips/clips.json`). I WAV sono
spezzoni di trasmissioni televisive, materiale di terzi che non va distribuito, quindi
`.gitignore` li esclude. Si mettono in locale con:

```
python tools/build_manifest.py C:\DEV\Claude\fff --out app/src/main/assets/clips
```

Conseguenza: l'APK che costruisce la CI **non contiene clip**. Serve a verificare che il
codice compili e che i test passino; l'APK da usare si costruisce in locale, con l'audio
al suo posto.

Una clip con `audio_present: false` o con il file mancante si importa ma resta nascosta, e
viene contata in un avviso in Libreria e in Oggi: è il percorso che si vede quando gli
asset non sono stati rigenerati.

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

### Test (JVM, senza emulatore)

| File | Cosa copre |
| --- | --- |
| `session/SpacedRepetitionTest` | intervalli per Livello, limite di 6 clip, precedenza della categoria della settimana, esclusione di clip nascoste e sospette, date finte |
| `audio/SilenceDetectorTest` | stop dopo 1,5 s di silenzio, pause brevi che non fermano, tetto di durata, taratura del rumore di fondo |
| `dsp/WavIoTest` | scrittura/rilettura di segnali sintetici, intestazione, writer incrementale, file troncati, chunk sconosciuti, stereo→mono, e un controllo sui 40 WAV veri |
| `dsp/WaveformTest` | inviluppo, RMS su float e su PCM 16 bit, casi limite |
| `data/ManifestMergeTest` | l'import non tocca mai i progressi, clip senza audio nascoste, misure del foglio, campi sconosciuti ignorati, manifest vero con 40 clip |
