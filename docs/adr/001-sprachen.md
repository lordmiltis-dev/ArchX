# ADR 0001: Programmiersprachen

- **Status:** Angenommen
- **Datum:** 2026-10-19

## Kontext

ArchX besteht aus einem Master und mehreren Submastern, die zum Teil unter Echtzeitbedingungen arbeiten (DMX mit 44 Hz, USB-Feedback an Motorfader). Jede Komponente soll die Sprache bekommen, die zu ihrer Aufgabe passt. Gleichzeitig soll das Projekt für Mitwirkende überschaubar bleiben, weil ArchX von anderen Entwicklern und Lichttechnikern erweitert werden soll.

## Entscheidung

| Bereich | Sprache |
|---|---|
| Master, GUI-Submaster (Compose Multiplatform) | **Kotlin** |
| Alle zeitkritischen und hardwarenahen Module: Divider/Assembler, DMX/Art-Net-Submaster, USB-Submaster, Pixel-Mapping-Submaster, Fixtures-Library-Submaster | **C++ (C++20)** |
| Savefile-Interpreter (Import, Konvertierung, Export) | **Rust** |
| Reverse-Engineering-Skripte und Prototypen (`tools/`) | Python, **nicht Teil des Produkts** |

## Begründung

- **Kotlin** ist produktiv, läuft auf der JVM und passt zu Compose Multiplatform. Der Master verarbeitet Logik im RAM. Der Hot-Path wird allokationsarm gehalten, damit GC-Pausen nicht stören.
- **C++** liefert geringe und gut vorhersehbare Latenz ohne Interpreter-Pausen, Zugriff auf libusb und ein reifes Ökosystem für Grafik und Hardware. Eine einzige native Sprache vermeidet doppelten Aufwand.
- **Rust** ist auf den Interpreter begrenzt. Er liest fremde, teils fehlerhafte Showfiles und enthält viel Konvertierungslogik. Dafür helfen Rusts Typsystem und die Speichersicherheit.
- Der **USB-Submaster** ist in C++ statt Python, weil Latenz und Jitter bei Motorfadern entscheidend sind. Er muss Motor- und Touch-Fader unterstützen.

## Verworfene Alternativen

- **Python für den USB-Submaster:** Der GIL und Interpreter-Jitter sind für den Motorfader-Pfad ungeeignet.
- **Rust für alle nativen Module:** Möglich, aber C++ bietet das größere Ökosystem für Grafik und Hardware-Bibliotheken.
- **Alles in Kotlin:** GC-Pausen im Echtzeitpfad sind ein Risiko.

## Konsequenzen

- Die CI braucht drei Toolchains (Gradle, CMake, Cargo).
- Schnittstellen zwischen den Sprachen müssen sprachneutral definiert werden (siehe ADR 0003).
- Der Rust-Interpreter läuft als eigener Prozess, weil er kein Kotlin-Datenmodell teilt.
- Python bleibt ein Hilfswerkzeug und gehört nicht zum Laufzeitsystem.
