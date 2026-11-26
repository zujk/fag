#!/usr/bin/env python3
"""Riassume l'esito di una run: errori di compilazione e test falliti.

Serve perche' i log delle Actions non si leggono senza accedere a GitHub: questo
riassunto finisce sul ramo `ci-rapporto`, che si scarica da raw.githubusercontent.
Uso: riassunto.py <gradle.log> <cartella test-results>
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

log_path, results_dir = sys.argv[1], sys.argv[2]
log = open(log_path, encoding="utf-8", errors="replace").read() if os.path.isfile(log_path) else ""

out = []
w = out.append

# --- errori del compilatore Kotlin/Java, una riga ciascuno -------------------
righe = [l.rstrip() for l in log.splitlines()]
compila = [l for l in righe if re.search(r"^e: |error: |^\s*> .*Compilation error", l)]
w("# Esito della run\n")
w("## Errori di compilazione: %d\n" % len(compila))
for l in compila[:200]:
    w("    " + l)
w("")

# --- test falliti, con messaggio e prima riga di stack utile -----------------
tot = falliti = 0
dettagli = []
for root, _, files in os.walk(results_dir):
    for f in sorted(files):
        if not f.endswith(".xml"):
            continue
        try:
            suite = ET.parse(os.path.join(root, f)).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter("testcase"):
            tot += 1
            for bad in list(case.findall("failure")) + list(case.findall("error")):
                falliti += 1
                testo = (bad.text or "").strip().splitlines()
                nostre = [r.strip() for r in testo if "com.studiovoce" in r]
                dettagli.append(
                    "### %s > %s\n\n    %s\n\n    %s\n"
                    % (
                        case.get("classname", "?"),
                        case.get("name", "?"),
                        bad.get("message", "(senza messaggio)").replace("\n", " ")[:500],
                        "\n    ".join(nostre[:6]) or (testo[0] if testo else ""),
                    )
                )

w("## Test: %d eseguiti, %d falliti\n" % (tot, falliti))
out.extend(dettagli)

# --- i task Gradle andati male ----------------------------------------------
andati_male = [l.rstrip() for l in righe if re.search(r"^(FAILURE|> Task .*FAILED|\* What went wrong)", l)]
if andati_male:
    w("\n## Task Gradle\n")
    for l in andati_male[:60]:
        w("    " + l)

# --- coda del log, se non si e' capito niente di preciso ---------------------
if not compila and not falliti:
    w("\n## Ultime righe del log\n")
    for l in righe[-80:]:
        w("    " + l)

print("\n".join(out))
