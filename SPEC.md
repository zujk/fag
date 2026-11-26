# Studio Fagnani — specifica dell'app Android

Versione 1.0 · 25 novembre 2026 · Destinataria: Claude Code

## 1. In una frase

App Android personale per l'allenamento vocale verso uno stile alla Francesca Fagnani. Ogni giorno guida una sessione breve: riscaldamento, esercizio della fase in corso, ripetizione di clip della voice bank con confronto della curva di altezza, ripasso a intervalli. Ogni settimana fa un controllo con le misure. Tutto funziona offline, sul telefono.

Utente unica: Ziv. Telefono: Nothing Phone (4a), Android. Lingua dell'interfaccia: italiano.

## 2. Principi

1. **Un tasto per iniziare.** La schermata principale ha un solo pulsante grande, "Inizia la sessione di oggi". L'app decide cosa fare in base a fase, giorno e ripasso. Le scelte manuali esistono, ma sono un livello sotto.
2. **Offline e privato.** Nessun permesso INTERNET nella v1. Le registrazioni della voce restano sul telefono. Il backup è un'esportazione manuale in un file zip.
3. **Clip come dati.** Le clip sono descritte da un manifest JSON. Per aggiungerne una non si tocca il codice.
4. **Tre contesti d'uso:** casa (voce alta, schermo), treno (silenzio, cuffie, solo ascolto e articolazione muta), macchina (voce alta, zero schermo, comandi con un tasto).
5. **Sicurezza vocale prima del volume di lavoro.** Dopo ogni sessione c'è una domanda sulla fatica. Se la voce è affaticata, il giorno dopo l'app propone solo una sessione leggera.

## 3. Stack tecnico (vincolante salvo motivo forte)

| Area | Scelta |
| --- | --- |
| Linguaggio | Kotlin 2.x |
| UI | Jetpack Compose + Material 3, tema chiaro e scuro, testo grande |
| Architettura | Un solo modulo `app`, pacchetti per funzione; MVVM con ViewModel + StateFlow; DI manuale (niente Hilt) |
| Persistenza | Room (SQLite) per progressi, registrazioni, misure; file in `filesDir` |
| Riproduzione | Media3 ExoPlayer (loop A-B, velocità 0,75x/1x, `MediaSession` per i tasti delle cuffie e del Bluetooth) |
| Registrazione | `AudioRecord`, PCM 16 bit mono, 44.100 Hz, salvata in WAV |
| DSP | In Kotlin puro, nel pacchetto `dsp`: YIN per l'altezza, LPC (Burg) per le formanti, DTW per allineare le curve. Nessuna libreria nativa nella v1 |
| Voce sintetica | `TextToSpeech` di Android, italiano, per le istruzioni in modalità macchina |
| SDK | minSdk 29, targetSdk ultimo stabile |
| Test | JUnit per il DSP (segnali sintetici), Compose UI test per i flussi principali |

## 4. Le clip

### 4.1 Clip incluse nell'app

Le 40 clip della voice bank sono incluse negli asset:

```
app/src/main/assets/clips/clips.json     ← manifest
app/src/main/assets/clips/*.wav          ← audio (WAV mono 44,1 kHz, 1-41 s)
```

`seed/clips.json` in questo pacchetto è il manifest di partenza, già compilato. Ziv rigenera manifest e audio dal suo foglio con:

```
python tools/build_manifest.py C:\DEV\Claude\fff --out app/src/main/assets/clips
```

Al primo avvio l'app importa il manifest nel database (tabella `clip`, `origin = bundled`). Se una versione nuova dell'app porta un manifest diverso, l'import aggiorna i metadati per `id`. Non tocca mai i progressi (Livello, storico, registrazioni). Una clip con `audio_present: false` o con il file mancante negli asset si importa ma resta nascosta, con un avviso nella libreria.

### 4.2 Schema del manifest (`schema_version: 1`)

```json
{
  "schema_version": 1,
  "categories": ["Domanda secca", "Domanda lunga", "Incalzare", "Ironia/sarcasmo",
                 "Risata", "Calma/seria", "Spontaneo fuori scena", "Reazione breve"],
  "clips": [{
    "id": "domanda-secca_01_corona",
    "file": "domanda-secca_01_corona.wav",
    "audio_present": true,
    "category": "Domanda secca",
    "category_id": "domanda-secca",
    "context": "In scena",
    "energy": "Calma",
    "study_note": "\"Che mestiere fa?\": domanda di 3 parole, intonazione piatta che scende alla fine",
    "level": null,
    "source": {"url": "https://www.youtube.com/watch?v=5v1SzWQ_ek4", "video_id": "5v1SzWQ_ek4",
               "start_s": 1.0, "end_s": 3.0, "label": "Belve 2023 – Fabrizio Corona"},
    "verification": "OK – pulito (Gemini 0:01-0:03)",
    "analysis": null,
    "origin": "bundled"
  }]
}
```

- `level`: voto di Ziv da 1 a 5 su quanto riesce a rifare la clip. `null` = mai votata.
- `analysis`: misure di `analizza_clip.py`, se presenti. Se mancano, l'app calcola da sola le sue misure al primo uso della clip (4.4) e le salva.
- Campi sconosciuti: si ignorano senza errore (estendibilità).

### 4.3 Aggiungere clip nuove dall'app

Flusso "Aggiungi clip" (dalla Libreria):

1. **Scegli l'audio:** un file dal telefono (selettore di sistema: WAV, M4A, MP3, OGG, anche video MP4, di cui si prende la traccia audio) oppure "Condividi con Studio Fagnani" da un'altra app.
2. **Ritaglia:** forma d'onda con due maniglie, ascolto del pezzo selezionato, zoom. Pulsanti di precisione ±0,1 s.
3. **Descrivi:** categoria (elenco del manifest + "Nuova categoria"), contesto, energia, nota su cosa studiare, fonte (testo libero o link), tutto opzionale tranne la categoria.
4. **Controllo:** l'app calcola altezza mediana e durata e mostra la curva. Avvisa se la clip dura meno di 1,5 s o se non trova parlato.
5. **Salva:** WAV mono 44,1 kHz in `filesDir/clips/`, riga nel database con `origin = user`.

Non scaricare da YouTube dentro l'app: il download resta compito di `scarica_clip.py` sul PC.

**Import a lotti:** "Importa cartella" accetta un `clips.json` più i suoi WAV (stesso schema, scelti con il selettore di cartelle). Così le clip aggiunte sul PC con il foglio arrivano nell'app senza ricompilarla.

**Export:** "Esporta voice bank" produce un `clips.json` con tutte le clip `user` e i Livelli aggiornati, per riportarli nel foglio.

### 4.4 Misure su ogni clip (calcolate una volta, in cache)

- Curva di altezza: YIN, finestra 40 ms, passo 10 ms, ricerca tra 75 e 500 Hz, soglia 0,15. Frame non sonori = `null`.
- Altezza mediana, 10° e 90° percentile.
- Formanti F1 e F2 mediane sui frame sonori: LPC Burg di ordine 10, ricampionato a 11 kHz, pre-enfasi 0,97.
- Durata, percentuale di parlato sonoro.

## 5. Il percorso guidato

### 5.1 Le fasi (dal documento "Metodo Fagnani")

| Fase | Quando | Cosa fa la sessione quotidiana |
| --- | --- | --- |
| 0 · Misura | Settimana 1 | Tre registrazioni guidate (brano fisso, 1 minuto libero, una domanda di Fagnani). Scheda bersaglio |
| 1 · Supporto | Settimane 2-8 | Riscaldamento + esercizi di risonanza, peso e pavimento di altezza. Le clip di Fagnani solo in ascolto |
| 2 · Stile a frasi | Settimane 4-12 | Ripetizione di clip corte (fino a 10 s), una categoria a settimana, con confronto delle curve |
| 3 · Shadowing | Mesi 3-6 | Shadowing sulle clip lunghe (15 s e oltre) + "intervista alla Belve" libera, registrata |
| 4 · Uso quotidiano | Mesi 6-12 | Sessione breve di mantenimento + missioni di trasferimento ("oggi una domanda alla Fagnani a un collega") |

Le fasi 1 e 2 si sovrappongono: dalla settimana 4 la sessione contiene entrambe. Il passaggio di fase è suggerito dall'app in base alle settimane e ai controlli, ma Ziv lo conferma sempre lei (Impostazioni → Fase). Ordine delle categorie nella fase 2: Domanda secca, Calma/seria, Ironia/sarcasmo, Incalzare, Domanda lunga, Reazione breve, Risata, Spontaneo fuori scena (configurabile).

### 5.2 Composizione della sessione (casa, ~10 minuti)

1. **Riscaldamento, 2 min:** humming, trilli, sirene. Ogni esercizio è una scheda con testo breve, timer e un audio guida opzionale.
2. **Blocco della fase, 4-5 min:** esercizi della fase 1 (schede con timer) e/o clip.
3. **Ripasso, 3 min:** clip scelte dal ripasso a intervalli (5.4).
4. **Chiusura, 15 s:** "Com'è la voce adesso?" → Bene / Un po' stanca / Affaticata, con nota opzionale.

Se nel controllo di chiusura Ziv risponde "Affaticata" per 2 sessioni di fila, la sessione successiva è solo il riscaldamento leggero. In quel caso compare il messaggio: "Se la stanchezza continua per qualche giorno, conviene sentire un logopedista."

### 5.3 Esercizio su una clip (il cuore dell'app)

Schermata con: titolo della nota, categoria, contesto ed energia come etichette, forma d'onda.

1. **Ascolta:** la clip suona 3 volte, con una pausa di 1 s tra una e l'altra. In sovrimpressione, la curva di altezza di Fagnani.
2. **Ripeti:** beep, registrazione automatica che si ferma da sola dopo 1,5 secondi di silenzio, oppure con un tocco. Durata massima pari a quella della clip × 2 + 2 s.
3. **Confronta:** suonano in fila la clip e la tua ripetizione. Grafico con le due curve sovrapposte, allineate nel tempo con DTW. Le altezze sono in semitoni rispetto alla mediana di ciascuna, così si confronta la **forma** della melodia e non l'altezza assoluta.
4. **Riscontro in parole, senza voti a punteggio:** al massimo due frasi generate da regole (5.5). Esempio: "La tua chiusura sale, la sua scende: prova a finire più in basso".
5. **Voto:** "Quanto ti è venuta?" 1-5 (aggiorna `level`), poi "Ancora" o "Prossima".

Controlli sempre visibili: velocità 0,75x, loop di una parte della clip (trascinando sulla forma d'onda), riascolta la tua ripetizione.

### 5.4 Ripasso a intervalli (dai Livelli)

- Livello `null`, 1 o 2 → di nuovo il giorno dopo.
- Livello 3 → tra 3 giorni.
- Livello 4 → tra 7 giorni.
- Livello 5 → tra 14 giorni.
- Ogni sessione prende fino a 6 clip scadute. Nella fase 2 hanno la precedenza quelle della categoria della settimana. Se non ce ne sono abbastanza, aggiunge clip nuove di quella categoria.
- Le clip con `analysis.check` che inizia per `SOSPETTA` o `MISTA` sono escluse dal ripasso finché Ziv non le segna "ascoltata, va bene".

### 5.5 Regole di riscontro (v1, deterministiche)

Calcolate sulle curve allineate in semitoni:

| Regola | Condizione | Messaggio |
| --- | --- | --- |
| Chiusura | Ultimi 300 ms: Fagnani scende di oltre 2 st, tu sali o resti piatta | "La sua chiusura scende, la tua no: finisci più in basso." |
| Escursione | La tua escursione (90°-10° percentile) è sotto il 60% della sua | "La sua melodia si muove di più: esagera un po' gli accenti." |
| Escursione eccessiva | La tua è sopra il 160% della sua | "Stai cantando un po': più asciutta." |
| Durata | La tua dura meno del 75% della sua | "Vai più piano, lascia le pause." |
| Durata | La tua dura più del 140% della sua | "Più sciolta: stai trascinando." |
| Tutto ok | Nessuna regola scattata | "Forma molto vicina. Prova a votarti." |

Al massimo due messaggi, in quest'ordine di priorità. Le soglie stanno in un file di configurazione Kotlin, non sparse nel codice.

### 5.6 Controllo della domenica

Una notifica ricorda il controllo la domenica alle 10:00 (orario configurabile). Il controllo prevede:

1. Lettura del brano fisso (il testo si imposta nella fase 0 ed è sempre lo stesso).
2. Un minuto di parlato libero su una domanda a caso da un elenco.
3. Calcolo di altezza mediana, 10° e 90° percentile, F1 e F2 mediane. Le misure si salvano con la data.
4. **Schermata Progressi:** un grafico a linee per ogni misura nel tempo, con la fascia di Fagnani (media delle clip con `check` OK) come riferimento orizzontale. Il confronto "settimana 1 / oggi" mette in fila le due registrazioni del brano fisso.

## 6. Le tre modalità

| | Casa | Treno | Macchina |
| --- | --- | --- | --- |
| Voce alta | Sì | No | Sì |
| Schermo | Sì | Sì | No |
| Contenuto | Sessione completa (5.2) | Ascolto delle clip del giorno con il testo della nota, segni di pausa e accento da toccare sullo schermo, "shadowing muto" (la clip suona, nessuna registrazione) | Sessione audio: TTS legge le istruzioni, suona la clip, beep, registra, suona clip + ripetizione, passa alla successiva. Niente grafici, niente voti durante la guida |
| Avvio | Pulsante principale | Selettore modalità in alto | Pulsante "Macchina", oppure in automatico quando si collega un dispositivo Bluetooth scelto nelle Impostazioni |
| Comandi | Tocco | Tocco | Tasto play/pausa di cuffie/Bluetooth (`MediaSession`): un tocco = avanti, due tocchi = ripeti la clip |

Modalità macchina, requisiti fermi:

- Schermo a tutta pagina nero con un unico pulsante gigante (pausa/riprendi) e il nome della clip.
- Nessuna richiesta di interazione: la sessione procede da sola.
- Le ripetizioni registrate in macchina si salvano con il flag `context = car`. Il voto si chiede dopo, alla prima apertura dell'app da ferma ("Hai 5 ripetizioni dalla macchina da votare").
- Audio in uscita sul dispositivo di sistema predefinito: Bluetooth, trasmettitore FM o altoparlante. Nessuna ipotesi sull'impianto dell'auto.

## 7. Schermate

1. **Oggi:** pulsante "Inizia la sessione di oggi", sotto la fase e la settimana ("Fase 2 · settimana 6 · categoria: Ironia"), le clip in ripasso oggi (numero), una riga sull'ultimo controllo. Selettore di modalità Casa/Treno/Macchina.
2. **Sessione:** passi in sequenza con una barra di avanzamento; l'esercizio su clip come in 5.3.
3. **Libreria:** elenco delle clip con filtri per categoria, contesto, energia, Livello, stato del controllo. Ogni clip ha la sua scheda: nota, fonte (link apribile nel browser), curva, Livello, storico delle ripetizioni, "Esercitati ora". Da qui si fanno anche Aggiungi clip, Importa cartella ed Esporta.
4. **Progressi:** grafici del controllo settimanale (5.6), Livello medio per categoria (barre orizzontali, obiettivo 5 clip a 4+ per categoria), giorni di pratica (calendario).
5. **Impostazioni:** fase corrente, brano fisso, orari dei promemoria, dispositivo Bluetooth della macchina, soglie (avanzate), backup/ripristino in zip, cancella tutte le registrazioni.

## 8. Dati (Room)

```
clip(id PK, file, origin[bundled|user], category, category_id, context, energy, study_note,
     source_url, source_label, start_s, end_s, verification, check_status, user_ok_override,
     hidden, created_at)
clip_analysis(clip_id PK, f0_med, f0_p10, f0_p90, f1_med, f2_med, duration_s, voiced_pct, contour_blob)
clip_progress(clip_id PK, level, last_practiced_at, due_at, reps_total)
attempt(id PK, clip_id, created_at, context[home|train|car], wav_path, duration_s,
        contour_blob, f0_med, feedback_codes, self_rating)
session(id PK, started_at, ended_at, phase, mode, fatigue[ok|tired|strained], note)
checkpoint(id PK, created_at, reading_wav, free_wav, f0_med, f0_p10, f0_p90, f1_med, f2_med)
exercise(id PK, phase, title, instructions, duration_s, audio_asset)   ← dal file exercises.json
settings (DataStore)
```

`contour_blob`: FloatArray in semitoni, codificata in binario, NaN per i frame non sonori.

Gli esercizi di riscaldamento e della fase 1 si definiscono in `assets/exercises.json`, con lo stesso approccio "dati, non codice": `id`, `phase`, `title`, `instructions`, `duration_s`, audio guida opzionale. Il set iniziale lo scrive Claude Code: almeno 8 esercizi, cioè humming in avanti, trilli delle labbra, sirena, "ng" verso vocali, vocali bright/dark a confronto, frase al pavimento di altezza, lettura di 3 frasi tenendo la risonanza, yawn-sigh di reset. Testi brevi, in italiano, senza gergo.

## 9. Pacchetti e struttura

```
app/src/main/java/.../studiovoce/
  data/        Room, DAO, import manifest, export/backup
  dsp/         Yin.kt, LpcFormants.kt, Dtw.kt, WavIo.kt, Contour.kt
  audio/       Recorder.kt (AudioRecord + VAD a energia), Player.kt (ExoPlayer + MediaSession)
  session/     SessionPlanner.kt (fase, giorno, ripasso → lista di passi), SpacedRepetition.kt, FeedbackRules.kt
  ui/          today/, session/, library/, progress/, settings/, car/, train/, components/
assets/clips/  clips.json + wav
assets/exercises.json
tools/build_manifest.py
```

`SessionPlanner` è una funzione pura (stato in ingresso → lista di passi), testata a parte.

## 10. Milestone e criteri di accettazione

**v0 · Libreria e ripetizione (prima consegna)**

- Le 40 clip compaiono, filtrabili e riproducibili; loop A-B e 0,75x funzionano.
- Esercizio su clip: ascolta → ripeti (registrazione con stop automatico sul silenzio) → riascolto in fila.
- Livello salvabile; il ripasso a intervalli sceglie le clip giuste (test unitario con date finte).
- Funziona in modalità aereo.

**v1 · Curve e percorso guidato**

- YIN: su seni sintetici da 100, 180, 250 Hz, errore sotto l'1%. Su un seno che scivola 150 → 250 Hz, la curva è monotona.
- Confronto con curve sovrapposte allineate DTW e riscontro in parole (5.5), con test sulle regole.
- Pulsante "Inizia la sessione di oggi" con fasi, riscaldamento, ripasso e domanda sulla fatica.
- Fase 0 guidata e scheda bersaglio.

**v2 · Modalità macchina e treno**

- Sessione in macchina completa senza toccare lo schermo; tasto del Bluetooth funzionante; voti differiti.
- Modalità treno senza microfono.

**v3 · Misure, progressi, estendibilità**

- Formanti LPC: su vocali sintetiche (F1 700, F2 1200 Hz), errore sotto il 10%.
- Controllo della domenica con grafici dei progressi.
- Aggiungi clip con ritaglio, Importa cartella, Esporta, backup e ripristino zip.

**Opzionale, dopo la v3:** somiglianza di voce on-device (modello di speaker embedding in ONNX Runtime Mobile), per marcare le clip aggiunte che contengono un'altra voce. Non va fatto prima: richiede un modello da qualche decina di MB e il permesso di scaricarlo o di includerlo.

## 11. Cosa non fare

- Nessun punteggio di "femminilità" o di "genere" della voce. Il riscontro riguarda la forma della melodia, il tempo, le misure: mai un verdetto sull'identità.
- Nessun download da YouTube nell'app.
- Nessun account, analytics o crash reporting di terzi.
- Nessuna funzione che chieda di parlare più forte, più a lungo o più in alto per "superare" un obiettivo.
