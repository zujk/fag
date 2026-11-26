#!/usr/bin/env python3
"""Genera clips.json (manifest dell'app Studio Voce) dal foglio Voice_bank_Fagnani.xlsx.

Uso:
    python tools/build_manifest.py <cartella-con-foglio-e-clip> [--out app/src/main/assets/clips]

- Legge la scheda "Clip" (colonne A-H) e, se c'è, la scheda "Analisi" (misure di analizza_clip.py).
- Copia i WAV da <cartella>/clip/ in <out>/ e scrive <out>/clips.json.
- Le clip senza file WAV vengono incluse con "audio_present": false e saltate dall'app.
Requisiti: pip install openpyxl
"""
import argparse, json, re, shutil, sys
from pathlib import Path
import openpyxl

CATEGORIES = ["Domanda secca", "Domanda lunga", "Incalzare", "Ironia/sarcasmo",
              "Risata", "Calma/seria", "Spontaneo fuori scena", "Reazione breve"]


def secs(t):
    s = 0.0
    for p in t.split(":"):
        s = s * 60 + float(p)
    return s


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src", type=Path, help="cartella con Voice_bank_Fagnani*.xlsx e clip/")
    ap.add_argument("--out", type=Path, default=Path("app/src/main/assets/clips"))
    ap.add_argument("--no-copy", action="store_true", help="scrive solo clips.json")
    a = ap.parse_args()

    sheets = sorted(a.src.glob("Voice_bank_Fagnani*.xlsx"), key=lambda p: p.stat().st_mtime)
    sheets = [p for p in sheets if ".bak" not in p.name and not p.name.startswith("~$")]
    if not sheets:
        sys.exit("Foglio non trovato")
    wb = openpyxl.load_workbook(sheets[-1], data_only=True)
    ws = wb["Clip"]

    analysis = {}
    if "Analisi" in wb.sheetnames:
        wa = wb["Analisi"]
        hdr = [c.value for c in wa[1]]
        for r in wa.iter_rows(min_row=2, values_only=True):
            if r[0]:
                analysis[r[0]] = dict(zip(hdr, r))

    a.out.mkdir(parents=True, exist_ok=True)
    clips = []
    for r in ws.iter_rows(min_row=2, values_only=True):
        fname = r[0]
        if not fname:
            continue
        fonte = r[6] or ""
        url = re.search(r"https?://\S+", fonte)
        rng = re.search(r"\[(\d+:\d+(?::\d+)?)\s*-\s*(\d+:\d+(?::\d+)?)\]", fonte)
        label = fonte.split("]", 1)[1].strip() if "]" in fonte else ""
        vid = re.search(r"v=([\w-]{11})", fonte)
        wav = a.src / "clip" / fname
        if wav.exists() and not a.no_copy:
            shutil.copy2(wav, a.out / fname)
        an = analysis.get(fname, {})
        clips.append({
            "id": Path(fname).stem,
            "file": fname,
            "audio_present": wav.exists(),
            "category": r[1],
            "category_id": slug(r[1] or ""),
            "context": r[2],                 # "In scena" | "Fuori scena"
            "energy": r[3],                  # "Calma" | "Incalzante" | "Divertita" | "Seria"
            "study_note": r[4],
            "level": r[5],                   # 1-5 o null
            "source": {
                "url": re.sub(r"[&?]t=\d+s?", "", url.group(0)) if url else None,
                "video_id": vid.group(1) if vid else None,
                "start_s": secs(rng.group(1)) if rng else None,
                "end_s": secs(rng.group(2)) if rng else None,
                "label": label,
            },
            "verification": r[7] if len(r) > 7 else None,
            "analysis": {
                "f0_median_hz": an.get("F0 mediana (Hz)"),
                "f0_p10_hz": an.get("F0 10° perc."),
                "f0_p90_hz": an.get("F0 90° perc."),
                "f1_median_hz": an.get("F1 mediana (Hz)"),
                "f2_median_hz": an.get("F2 mediana (Hz)"),
                "speaker_similarity": an.get("Somiglianza"),
                "check": an.get("Esito"),
            } if an else None,
            "origin": "bundled",
        })
    manifest = {"schema_version": 1, "categories": CATEGORIES, "clips": clips}
    (a.out / "clips.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    n_audio = sum(c["audio_present"] for c in clips)
    print(f"{len(clips)} clip nel manifest, {n_audio} con audio -> {a.out}")


if __name__ == "__main__":
    main()
