# ADR 0002: Lizenz

- **Status:** Vorläufig
- **Datum:** 2026-10-19

## Kontext

ArchX soll von anderen Entwicklern und Lichttechnikern erweitert werden können. Gleichzeitig soll nicht ein Unternehmen mit der Idee und dem Code Geld verdienen, ohne dass der Autor daran beteiligt ist. Wenn mit ArchX Geld verdient wird, soll es der Autor sein.

## Entscheidung

Vorläufig wird **GPLv3** verwendet. Solange der Autor alleiniger Rechteinhaber ist, kann die Lizenz für neuen Code noch angepasst werden.

## Begründung

Die GPL verpflichtet jeden, der den Code verändert und weitergibt, seine Änderungen ebenfalls offenzulegen. Das passt zum Ziel eines offenen, erweiterbaren Frameworks.

## Einschränkung

Die GPL verbietet **keine kommerzielle Nutzung**. Auch Unternehmen dürfen GPL-Software verkaufen und Dienstleistungen darum anbieten. Das Ziel "nur der Autor verdient daran" wird dadurch allein nicht erreicht.

## Offene Punkte

- **AGPLv3 statt GPLv3**, um die Lücke zu schließen, dass jemand die Software als Online-Dienst anbietet, ohne den Code zu teilen.
- **Dual-Licensing:** Offener Code unter GPL, zusätzlich eine kommerzielle Lizenz für Firmen, die den Code in geschlossene Produkte einbauen wollen.
- **CLA (Contributor License Agreement)** für externe Beiträge. Ohne CLA ist Dual-Licensing oder eine spätere Lizenzänderung nicht möglich.
- **Lizenz der Extension-API und des SDK:** Hersteller sollen eigene Treiber-Extensions schreiben können. Möglich sind eine permissivere Lizenz für die API (z.B. LGPL oder Apache) oder eine ausdrückliche Extension-Ausnahme.
- **Kompatibilität der Abhängigkeiten** (libusb, ZeroMQ, Skia usw.) mit der gewählten Lizenz prüfen.
- Rechtliche Beratung durch einen Anwalt für Open-Source-Recht vor dem ersten externen Beitrag oder einem kommerziellen Angebot.

## Fremde Inhalte

Die Onyx-Fixture-Library, DyLOS-Content und echte Onyx-Showfiles gehören nicht zum Repository. Nutzer laden sie selbst herunter. Im Repo bleiben sie über `.gitignore` (`/local-data/`) ausgeschlossen.

## Konsequenzen

- Eine `LICENSE`-Datei muss vorhanden sein, **bevor** das Repository öffentlich wird.
- Bis zur endgültigen Entscheidung werden keine externen Beiträge ohne CLA angenommen.
