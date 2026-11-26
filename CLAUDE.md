# Studio Fagnani — istruzioni per Claude Code

Leggi `SPEC.md` per intero prima di scrivere codice. È la fonte di verità; se qualcosa non torna, chiedi prima di inventare.

## Come lavorare
- Procedi per milestone (SPEC §10): v0, poi v1, v2, v3. Una milestone alla volta, chiusa solo quando i suoi criteri di accettazione passano.
- All'inizio di ogni milestone: proponi un piano breve (file da creare, ordine), poi implementa.
- Il DSP (`dsp/`) va scritto test-first con segnali sintetici: è la parte che non si può verificare "a orecchio" in emulatore.
- `SessionPlanner`, `SpacedRepetition` e `FeedbackRules` sono logica pura: test unitari completi.
- Build: `./gradlew assembleDebug`; test: `./gradlew test`. Non chiudere un task con build o test rossi.

## Vincoli
- Kotlin + Jetpack Compose + Room + Media3. Niente Hilt, niente librerie DSP native nella v1.
- Nessun permesso INTERNET. Nessun analytics.
- Interfaccia in italiano, testi brevi e concreti, nessun gergo tecnico verso l'utente.
- Clip e esercizi sono dati (`assets/clips/clips.json`, `assets/exercises.json`): mai metadati di clip nel codice.
- L'import del manifest non deve mai cancellare progressi o registrazioni.

## Clip
- Il manifest di partenza è in `seed/clips.json` (40 clip). Copialo in `app/src/main/assets/clips/` all'inizio della v0.
- I WAV non sono nel repo finché l'utente non lancia `python tools/build_manifest.py <cartella> --out app/src/main/assets/clips`. Fino ad allora l'app deve funzionare con le clip senza audio nascoste e un avviso chiaro in Libreria.
- Per i test strumentali usa clip sintetiche generate nei test, non quelle vere.
