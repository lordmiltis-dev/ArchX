# ArchX

> **🚧 WORK IN PROGRESS — ArchX ist noch nicht fertig.** Die Funktionen, Schnittstellen und diese Dokumentation können sich während der aktiven Entwicklung ändern. Das Projekt ist noch nicht für den Produktionseinsatz vorgesehen.
>
> **KI-Hinweis:** KI-Werkzeuge können bei Entwicklung und Dokumentation eingesetzt werden. Architektur und Umsetzungsrichtung werden vom Projektinhaber bestimmt und jede Einbindung fremder Änderungen wird geprüft.

<img src="docs/img/ArchX_Logo_ohne_Hintergrund.png" width="200" height="200">

Ein geplantes Lighting-Software-Framework für moderne und ältere digitale Lightingpulte. Ziel ist die Anbindung unterschiedlicher Systeme über Erweiterungs-Slots und Hardware-Treiber.

ArchX soll um ein zentralen Master-State-Modell aufgebaut werden, das Showdaten verwaltet und über spezialisierte Submaster für Hardware, Protokolle, Rendering, Benutzeroberfläche und Dateiformate mit ihnen kommuniziert. Die Modulgrenzen sind als Entwicklungsziel vorgesehen und müssen noch vollständig umgesetzt und geprüft werden.

## Geplante Funktionen

Die folgenden Punkte beschreiben den aktuellen Entwicklungsstand und geplante Ziele. Nicht alle Funktionen sind bereits implementiert:

- Eine modulare Architektur mit einem zentralen Master und austauschbaren Submastern
- Anbindung moderner und älterer digitaler Lightingpulte über Erweiterungs-Slots und Hardware-Treiber
- Geplante USB-Konsolenintegration mit automatischer Treibererkennung und bidirektionaler Datenübertragung
- Geplante DMX- und Art-Net-Ausgabe mit optionaler RDM-Unterstützung
- Geplantes Pixel Mapping sowie DyLOS-kompatible Wertgenerierung aus einer zukünftigen Master-2D-Liste
- Konsistenter Show-State im Speicher mit GUI-Aktualisierung aus dem Master
- Geplantes Speichern und automatisches Speichern von Showfiles im Format `.archx`
- Import und Export über Erweiterungen
- Erweiterungsökosystem für Hardware, UI, Fixtures, Ausgänge, Importer, Exporter und Pixel Mapping
- Geplante sichere Speichersemantik durch temporäre Dateien und atomare Ersetzung
- Geplanter automatischer Rotation von Backups zur Absicherung bei Absturz oder Stromausfall

## Architekturübersicht

Das geplante Projekt ist in einen Master für den Show-State und mehrere spezialisierte Submaster für Hardware, Schnittstellen und Verarbeitung gegliedert. Diese Struktur soll unterschiedliche Hardware- und Softwarekomponenten modular kombinierbar machen.

<img src="docs/img/ArchX-Architecture.png" alt="Geplanter Architekturüberblick">

### Hauptkomponenten

| Komponente | Sprache | Geplante Verantwortung |
| --- | --- | --- |
| Master | Kotlin | Verwaltung des Showfile-State im RAM und Koordination der Submaster |
| GUI-Submaster | Kotlin | Benutzeroberfläche und UI-Erweiterungen |
| Divider / Assembler | C++ | Bündelung eingehender Events und Verteilung an USB- und DMX-Komponenten |
| USB-Submaster | C++ | Konsolenerkennung, Laden geeigneter Treibererweiterungen und Datenweiterleitung |
| DMX / Art-Net-Submaster | C++ | Geplante regelmäßige Übertragung von DMX-Daten und optionale RDM-Unterstützung |
| Pixel-Mapping-Submaster | C++ | Geplante Wertgenerierung aus der Master-2D-Liste |
| Fixtures-Library-Submaster | C++ | Verwaltung von Fixture-Profilen über Erweiterungen |
| Savefile-Interpreter | Rust | Geplantes Laden, periodisches Speichern und Exportieren von Showfiles |

## Geplanter Datenfluss

Die folgenden Abläufe beschreiben die Architekturziele. Sie stellen keine Zusage zur aktuellen Implementierung dar.

### Eingabe: Konsole zur Schnittstelle

Pult → USB-Submaster (Treibererweiterung) → Divider/Assembler → Master → GUI-Submaster

### Ausgabe: DMX und Art-Net

Master → Divider → DMX-Submaster → optionaler RDM-Connector → DMX-Ausgang oder Art-Net-Ausgang

### Pixel Mapping

Der Master soll Daten einer geplanten 2D-Liste bereitstellen, aus denen der Pixel-Mapping-Submaster Werte erzeugt. Diese sollen anschließend wieder in den Master übernommen werden.

### Feedback zur Konsole

Geplant ist ein Rückweg über Master → Divider → USB-Submaster → Treibererweiterung zur Konsole für LEDs, Motorfader und andere Feedbackdaten.

### Speichern und Laden

Der Savefile-Interpreter soll den Master-State periodisch lesen und als `.archx`-Showfile schreiben. Fremde Showfile-Formate sollen durch Importer-Erweiterungen unterstützt werden.

## Kernentscheidungen

### Laufzeit mit mehreren Sprachen

Die geplante Aufteilung verwendet mehrere Sprachen für unterschiedliche Verantwortungsbereiche:

- Kotlin für Master und UI
- C++ für Hardware-Nähe und native Submaster
- Rust für die Savefile-Verarbeitung und Persistenz

Diese Aufteilung soll die Laufzeit entlasten und zeitkritische Hardwarepfade begrenzen. Die Sprachgrenzen und die Kommunikation sind weiterhin Gegenstand der Entwicklung.

### Gemeinsames Kommunikationsschema

Protocol Buffers (`proto3`) sollen für Nachrichten, Statusframes und die `.archx`-Struktur verwendet werden. Das Schema soll unter `schema/` liegen; Kotlin-, C++- und Rust-Code soll während des Builds erzeugt werden.

Dies soll einen gemeinsamen Vertrag für Show-State, Events und Dateiinhalte schaffen und binäre Layouts innerhalb der Komponenten vermeiden.

### Geplantes `.archx`-Dateiformat

Das geplante Showfile-Format ist eine binäre Datei mit festem Header und eingebetteter Protobuf-Nutzlast:

- Magic-Identifikator `ARCHX`
- Formatversion
- Länge der Protobuf-Nutzlast
- Protobuf-Block mit Show-Definitionen

Dieses Format soll kompakte Dateien, Versionsprüfungen und sichere Wiederherstellung ermöglichen.

### Medienverwaltung und Speichersicherheit

Medien wie Bilder, Videos und Pixel-Mapping-Inhalte sollen außerhalb der Showfile in einem benachbarten Medienordner abgelegt werden. Die `.archx`-Datei soll Referenzen, Prüfsummen und relative Pfade speichern.

Für sichere Schreibvorgänge ist vorgesehen, zunächst eine temporäre Datei zu schreiben und erst nach Abschluss atomar zu ersetzen. Dadurch soll das Risiko beschädigter Dateien bei Abstürzen oder Stromausfällen reduziert werden.

### Geplante Backups und Wiederherstellung

Für die erste Entwicklungsphase ist eine rotierende Backup-Strategie geplant:

- Vollständige Show-Backups sollen regelmäßig in `autosave/` geschrieben werden
- Dateien sollen laufende Nummern erhalten und in Rotation gehalten werden
- Wenn die Hauptdatei fehlt oder ungültig ist, soll das neueste gültige Backup verwendet werden
- Backups sollen nur bei Änderungen des Show-State geschrieben werden

Diese Strategie soll Datenverluste bei Live-Betrieb begrenzen, ohne früh zu einem komplexen Change-Journal zu führen.

### Geplante lokale Datenablage

Projektdaten sollen in betriebssystemabhängigen Benutzerverzeichnissen gespeichert werden:

- Linux: `~/.local/share/archx/`, `~/.config/archx/`, `~/.cache/archx/`
- Windows: `%APPDATA%\ArchX\` und `%LOCALAPPDATA%\ArchX\`

Die Datenablage soll Bibliotheken, Showfiles, Autosaves und Cache-Daten voneinander trennen.

## Geplante Erweiterungstypen

- Hardwaretreiber für Consolen
- Ausgabemodule für DMX und RDM
- Importer und Exporter für Showfiles
- Fixture-Bibliotheken
- Pixel-Mapping-Bibliotheken sowie Video- und Bildimporter
- UI-Erweiterungen

## Projektstruktur

- `master/` – Kotlin-basierte Master-Anwendung und geplanter Show-State
- `interpreter/` – Rust-basierter Savefile-Interpreter und Persistenzschicht
- `native/` – C++-Submaster und native Laufzeitkomponenten
- `schema/` – Protokoll- und Showfile-Schemas
- `docs/` – Projektdokumentation, Architektur und ADRs

## Roadmap

ArchX befindet sich noch in aktiver Entwicklung. Die folgenden Punkte sind breite Entwicklungsziele und keine Zusagen zu veröffentlichten Funktionen:

1. Cross-language Schemas und Kommunikationspfade zwischen Kotlin, C++ und Rust validieren.
2. Kern-Master und Showfile-Verarbeitung einschließlich sicherem Speichern und Autosave/Recovery weiterentwickeln.
3. Erweiterungsschnittstellen für Konsolenhardware, DMX/Art-Net, Fixture-Bibliotheken, Pixel Mapping und UI entwickeln.
4. Import-/Export-Integrationen und Medienverarbeitung ergänzen und testen.
5. Dokumentation, automatische Tests und Kompatibilitätsabdeckung mit zunehmender Reife verbessern.

## Mitwirken

Beiträge sind willkommen, insbesondere Erweiterungsarbeiten. Vor der Entwicklung einer Erweiterung sollten neue Änderungen in einem eigenen Branch erfolgen und nicht direkt auf einen gemeinsam genutzten Branch gepusht werden. Nach Fertigstellung kann ein Pull Request geöffnet werden.

Der Projektinhaber oder ein benannter Maintainer prüft Beiträge vor einer Übernahme in `main` oder einen anderen gemeinsamen Branch. KI-Werkzeuge dürfen zur Unterstützung verwendet werden, dürfen aber keine Verantwortung für unkontrollierte oder nicht überprüfte Änderungen übertragen.

## Dokumentation

- [Architekturübersicht](docs/architecture.md)
- [ADR 0001: Programmiersprachen](docs/adr/001-sprachen.md)
- [ADR 0002: Lizenz](docs/adr/002-lizenz.md)
- [ADR 0003: Schemaformat](docs/adr/003-ipc-format.md)
- [ADR 0005: `.archx` Dateiformat](docs/adr/005-archx-dateiformat.md)
- [ADR 0006: Speicherorte](docs/adr/0006-speicherorte.md)
- [ADR 0007: Autosave-Strategie](docs/adr/0007-autosave.md)

## Technologie-Stack

- Kotlin – vorgesehen für Master und UI
- Rust – vorgesehen für Savefile-Interpreter und Dateiverarbeitung
- C++ – vorgesehen für Submaster, Hardware, DMX und native Verarbeitung
- CMake – für die native Build-Integration
- Protobuf – für geplante Cross-language-Nachrichten und Dateistrukturen

## Lizenz

ArchX steht unter der GNU General Public License v3.0. Siehe [LICENSE.md](LICENSE.md). GPLv3 erlaubt Weitergabe auch gegen Gebühr, sofern die Lizenzbedingungen eingehalten werden.

## Status

ArchX ist als modulare Lighting-Software geplant und soll langfristig ältere und neuere Konsolenökosysteme über erweiterbare Schnittstellen verbinden. Das Projekt ist noch nicht fertig, noch nicht vollständig validiert und nicht für den Produktionseinsatz freigegeben.
