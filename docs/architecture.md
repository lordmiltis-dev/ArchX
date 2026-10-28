# Architektur

Stand: 2026-10-20

ArchX besteht aus einem Master, der den gesamten Showfile-Zustand im RAM hält, und spezialisierten Submastern. Alles Hardware-Spezifische, jedes Dateiformat und jede Fixture-Library wird über Extensions angebunden.

<img src="docs/img/ArchX-Architecture.png">

## Komponenten

| Komponente | Sprache | Aufgabe |
|---|---|---|
| Master | Kotlin | Hält den Showfile-Zustand im RAM und verwaltet alle Submaster |
| GUI-Submaster | Kotlin | Benutzeroberfläche (Compose Multiplatform), über UI-Extensions austauschbar |
| Divider / Assembler | C++ | Bündelt eingehende Events zu Status-Frames und verteilt Befehle des Masters auf USB- und DMX-Submaster |
| USB-Submaster | C++ | Erkennt angeschlossene Pulte, lädt die passende Treiber-Extension und tunnelt die Daten bidirektional (inklusive Motor- und Touch-Fader) |
| DMX / Art-Net-Submaster | C++ | Sendet DMX-Frames in einer 44-Hz-Schleife, optional mit RDM |
| Pixel-Mapping-Submaster | C++ | Berechnet aus der 2D-Liste des Masters die Pixelwerte (DyLOS-kompatibel) |
| Fixtures-Library-Submaster | C++ | Verwaltet Scheinwerferprofile, primär die Onyx-Library, weitere per Extension |
| Savefile-Interpreter | Rust | Importiert Showfiles nach `.archx`, sichert den Master-Zustand periodisch und exportiert über Export-Extensions |

Die Sprachwahl ist in [ADR 0001](adr/001-sprachen.md) begründet.

## Signalwege

**Eingabe (Pult zur Oberfläche):**
Pult → USB-Submaster (Treiber-Extension) → Divider/Assembler → Master → GUI-Submaster. Der Master spiegelt Änderungen an die Oberfläche zurück, damit sich Fader auf dem Bildschirm mitbewegen.

**Ausgabe DMX und Art-Net:**
Master → Divider → DMX-Submaster → Extension (optional RDM) → DMX Out am Pult bzw. Art-Net Out. RDM-Rückmeldungen laufen über den Assembler zurück zum Master.

**Pixel-Mapping:**
Der Master liefert die 2D-Liste an den Pixel-Mapping-Submaster. Dieser berechnet die Werte und gibt sie an den Master zurück. Der Master führt sie wie jede andere Wertquelle (z.B. Playbacks) zusammen und gibt sie über Divider und DMX-Submaster aus. Es gibt keinen Direktweg vom Pixel-Mapping zum DMX-Submaster, damit Merging, Grandmaster und Blackout nicht umgangen werden.

**Rückkanal zum Pult (LEDs, Motorfader):**
Master → Divider → USB-Submaster → Treiber-Extension → Pult.

**Speichern und Laden:**
Der Savefile-Interpreter liest den Master-Zustand periodisch aus, ohne ihn zu verändern, und speichert ihn als `.archx`. Fremde Showfiles (zunächst Onyx) werden über Importer-Extensions in `.archx` umgewandelt und in den Master geladen.

## Extension-Typen

- Hardware-Treiber für Pulte (werden beim ersten Anschließen automatisch zugewiesen)
- Ausgabe (DMX, RDM)
- Importer und Exporter für Showfiles
- Fixture-Library-Quellen
- Pixel-Mapping-Importer (neue Pixel-Mapping-Libraries, Videos, Bilder)
- UI

## Weiterführend

- [ADR 0001: Programmiersprachen](adr/001-sprachen.md)
- [ADR 0002: Lizenz](adr/002-lizenz.md)
- [ADR 0003: Schema-Format](adr/003-ipc-format.md)
