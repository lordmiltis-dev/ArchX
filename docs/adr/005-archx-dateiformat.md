# ADR 0005: Aufbau des .archx-Dateiformats und Umgang mit Medien

- **Status:** Vorläufig (die Grundentscheidung "Datei plus Medienordner" steht, die Details werden bei der Umsetzung in Phase 1 bestätigt)
- **Datum:** 2026-10-26

## Kontext

`.archx` ist das interne Master-Format von ArchX. Es soll das gesamte Showfile enthalten (Patch, Gruppen, Paletten, Cues, Playbacks, 2D-Plan) und schnell laden und speichern.

Pixel-Mapping-Inhalte, Videos und Bilder können sehr groß sein. Sie können außerdem in Cues gespeichert werden, sodass Cues auf sie verweisen. Würden sie in der Datei selbst liegen, würde jede Sicherung (auch die automatische) sehr lange dauern.

## Entscheidung

1. **`.archx` ist eine Binärdatei.** Sie besteht aus einem kleinen Kopf mit einer Kennung (zum Beispiel `ARCHX`) und der Formatversion. Danach folgt ein Protobuf-Block, der das Showfile beschreibt (siehe ADR 0003).
2. **Medien liegen nicht in der Datei, sondern in einem Medienordner neben ihr.** Die `.archx` speichert nur Verweise.
3. **Ein Verweis enthält eine eindeutige Kennung, eine Prüfsumme (Hash) des Inhalts und den relativen Pfad im Medienordner.** Cues speichern solche Verweise auf Pixel-Mapping-Inhalte, Videos und Bilder, nie die Daten selbst.
4. **Fehlende Medien verhindern das Laden nicht.** Das Showfile wird geladen, betroffene Cues werden als "Medium fehlt" gekennzeichnet, und der Nutzer sieht eine Liste.
5. **Zum Weitergeben gibt es eine Exportfunktion**, die Datei und Medienordner zu einem Archiv bündelt.
6. **Speichern erfolgt sicher:** Zuerst wird in eine temporäre Datei geschrieben, danach wird sie in einem Schritt umbenannt. Bei einem Absturz bleibt die alte Datei erhalten.
7. **Das Journal** (Änderungsprotokoll für Autosave, Undo und Absturzwiederherstellung) wird in einer eigenen ADR beschrieben.

## Begründung

- Die Datei bleibt klein, und Speichern und Autosave bleiben schnell.
- Mit der Prüfsumme erkennt ArchX, ob ein Medium verändert oder ersetzt wurde, und ein Verweis bleibt gültig, wenn Dateien umbenannt oder auf einen anderen Rechner kopiert werden. Identische Medien lassen sich außerdem nur einmal speichern.
- Kennung und Version im Kopf erlauben es, alte Dateien zu erkennen und bei Formatänderungen umzuwandeln.
- Das sichere Speichern verhindert kaputte Dateien bei Stromausfall oder Absturz, der gefährlichste Fall bei Live-Shows.

## Verworfene Alternativen

- **Alles in einer einzelnen Datei:** Einfach zu kopieren, aber bei Videos riesig und langsam beim Speichern.
- **Datei als ZIP-Container mit allem darin:** Ein Archiv müsste bei jedem Speichern neu geschrieben werden und bringt dieselben Größenprobleme mit.
- **Textformat (JSON):** Gut lesbar, aber größer und langsamer. Das Master-Format soll optimiert sein.

## Offene Punkte

- Benennung und Ort des Medienordners (zum Beispiel `<Dateiname>.media/`).
- Hash-Verfahren (zum Beispiel SHA-256) und Umgang mit sehr großen Dateien.
- Format des Exportarchivs.
- Umgang mit Medien, die dem Nutzer gehören und nicht lizenzfrei sind (siehe ADR 0004).

## Konsequenzen

- Der Savefile-Interpreter (Rust) muss Verweise lesen und schreiben können, ohne Mediendaten zu laden.
- Der Master hält nur Verweise im RAM. Mediendaten werden im Pixel-Mapping-Submaster geladen (siehe [Architektur](../architecture.md)).
- Zum Testen braucht Phase 1 Beispieldateien mit und ohne Medien, auch mit absichtlich fehlenden Medien.
