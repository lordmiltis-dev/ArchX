# Ideen und offene Punkte

Eine Merkliste für Dinge, die später kommen. Jeder Eintrag nennt, ab wann er relevant wird.

## Ideen

### Symmetrie- und Spiegelwerkzeug (2D-Plan, ab Phase 9)

Ein Werkzeug wie in Zeichenprogrammen: Man legt im 2D-Plan eine Form fest (zum Beispiel einen Stern aus Linien). Die Fixtures liegen in den Feldern der Form und verhalten sich je nach Position synchron bei Position, Farbe und Intensität, ähnlich einem Spiegel. Gedacht ist eine Referenzseite, die der Nutzer programmiert, und Folgeseiten, deren Werte daraus berechnet werden (gespiegelt oder gedreht).

Voraussetzungen:
- Position und Ausrichtung jedes Fixtures im 2D-Plan (neue Felder im Patch).
- Programmer, Effekte und Gruppen (Phase 5).

Offene Fragen:
- Spiegelung und Drehung für Pan und Tilt braucht die Ausrichtung der Fixtures (zum Beispiel hängend oder stehend).
- Überschneidung mit dem Pixel-Mapping: Soll Symmetrie auch auf Pixel-Mapping-Inhalte wirken?

### 3D-Map mit Blender-Import als Extension (ganz am Ende)

Eine 3D-Ansicht der Bühne, in die man eigene Objekte aus Blender importieren kann. Sie läuft als Extension und liest nur: Sie bekommt die Ausgabewerte des Masters und stellt sie dar, sie verändert die Show nicht.

Vorbereitung, die jetzt schon sinnvoll ist:
- Wenn die Patch-Liste später eine Position im Plan bekommt, gleich dreidimensional planen (x, y, z) und mit Drehung (Ausrichtung des Fixtures). Das hilft auch dem Symmetriewerkzeug bei Pan und Tilt.
- Die Show soll DMX auch als sACN ausgeben können, damit die 3D-Ansicht wie jeder andere Visualizer angebunden werden kann.

Offene Fragen:
- Austauschformate: glTF/GLB für Objekte aus Blender, GDTF (Fixtures, teilweise mit 3D-Modellen) und MVR (komplettes Rig) für den Austausch mit anderen Programmen.
- Technik der Darstellung (eigene Engine oder bestehende Bibliothek) und wie aufwendig Lichtkegel und Nebel sein sollen.

### Lichtkegel im 2D-Plan mit Scanner-Anbindung (2D-Plan-Extension, nach Phase 9)

Jeder Moving Head bekommt im 2D-Plan einen Lichtkegel. Eine Extension kann sich mit einer Scanner-Software verbinden, um den Raum oder die Fläche im Bereich des Moving Heads einzuscannen und im Plan einzufügen (für den 2D-Plan reicht die Bodenfläche). Der Nutzer stellt ein, in welche Richtung der Moving Head orientiert ist. Verschiebt er den Lichtkegel im Programm, folgt der echte Moving Head dieser Bewegung.

Aufteilung:
- **Im Master (Kern):** ein Berechnungsdienst, der aus einem Zielpunkt im Plan die Werte für Pan und Tilt berechnet. Ihn brauchen auch andere Werkzeuge (Symmetriewerkzeug, mögliche Tracking-Systeme).
- **In der Extension:** Import der Scan-Daten und Darstellung der Lichtkegel.

Voraussetzungen:
- Position im Plan mit Höhe (x, y, z) und Ausrichtung pro Patch (gleiche Felder wie beim Symmetriewerkzeug und der 3D-Map).
- Physische Daten pro Fixture: Linsenwinkel, Schwenk- und Neigungsbereich, Zuordnung von DMX-Wert zu Winkel (aus der Library, bewusst noch nicht im Schema).
- Kalibrierung: Der Moving Head wird auf einige bekannte Punkte am Boden gerichtet, ArchX berechnet daraus Ausrichtung und Versatz.

Offene Fragen:
- Welche Scanner und Dateiformate (Punktwolken, Netze) unterstützt werden.
- Umgang mit der Mehrdeutigkeit von Pan und Tilt (derselbe Punkt lässt sich mit zwei Stellungen erreichen) und mit der maximalen Geschwindigkeit der Köpfe.
- Sicherheit: Echte Moving Heads bewegen sich, wenn der Kegel verschoben wird.

### DMX-Interface-Extension als eigener Ausgabetyp (Phase 4)

Neben dem DMX-Ausgang am Pult ein eigener Extension-Typ für USB-DMX-Interfaces, damit auch Pulte ohne eigenen DMX-Ausgang und reine Controller nutzbar sind.

### Optionale Adressen für einzelne Zellen (bei Bedarf)

In Onyx zeigen Untereinträge "Auto" bei Universum und Adresse. Falls einzelne Zellen eine eigene Adresse brauchen, ergänzt man optionale Felder in `SubFixtureSettings`.

### Fixture-Zonen als eigenes Konzept (offen)

In Onyx gibt es "Zone"-Einträge ohne Universum und Adresse. In ArchX hat jeder Patch eine Adresse, Zonen wären also ein eigenes Konzept. Zuerst klären, wofür sie genutzt werden.

## Technische Punkte

- **DMX-Bereiche ("Capabilities") und Umschaltkanäle** in das Schema aufnehmen (Phase 5, wenn der Programmer gebaut wird).
- **Library-Cache:** Eingelesene Typen als Binärdatei im Cache-Ordner ablegen, später als Memory-Mapped-Datei von mehreren Submastern lesbar (Phase 6, ADR "Library-Cache").
- **Mehrere Threads im Master:** Snapshot und Änderungen dürfen sich nicht stören. Lösung beim Bau des Masters.
- **Dateikopf, sicheres Speichern und Journal** im Rust-Interpreter (Phase 1).
- **Extension-Form:** Prozesse, Bibliotheken oder WASM. Entscheidung in Phase 4.

## Vor der Veröffentlichung

- Logo als Vektor (SVG) mit gleichmäßiger Kontur, plus Variante für dunkle Hintergründe.
- `LICENSE`, `README.md` mit der Klarstellung zu Arch Linux, `.gitattributes`.
- Anfrage bei Obsidian und AtlaBase wegen Onyx-Library und DyLOS-Content (ADR 0004).
- Markenrecherche für Klassen 9, 41 und 42, bevor ArchX kommerziell wird.
- Lizenzfragen klären: GPL oder AGPL, Dual-Licensing, CLA, Lizenz der Extension-API (ADR 0002).
