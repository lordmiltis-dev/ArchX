# ADR 0007: Automatische Sicherungen statt Journal (vorerst)

- **Status:** Vorgeschlagen
- **Datum:** 2026-11-07

## Kontext

Bei einem Absturz oder Stromausfall soll möglichst wenig Arbeit verloren gehen. In ADR 0005 und in der Roadmap für Phase 1 war ein Journal vorgesehen: Jede Änderung wird als kleiner Eintrag in eine Zusatzdatei geschrieben und nach einem Absturz wieder abgespielt.

Das Speichern einer ganzen Show ist inzwischen sicher (Temp-Datei, dann Umbenennen) und bei den heutigen Datenmengen (Patch-Liste und Fixture-Typen) sehr schnell.

## Entscheidung

Für Phase 1 gibt es **automatische Sicherungen mit Rotation** statt eines Journals:

1. Der Savefile-Interpreter schreibt regelmäßig eine vollständige Sicherung der Show in den Ordner `autosave/` (siehe ADR 0006). Er benutzt dafür dasselbe sichere Speichern wie beim normalen Speichern.
2. **Dateiname:** `<Showname>-<laufende Nummer, 6-stellig>.archx`, zum Beispiel `Testshow-000042.archx`. Die Nummer steigt immer weiter und wird nicht wiederverwendet.
3. **Rotation:** Es bleiben nur die neuesten N Sicherungen, ältere werden gelöscht.
4. **Wiederherstellung:** Lässt sich die Hauptdatei nicht lesen (fehlt, abgeschnitten, beschädigt), nimmt der Interpreter die neueste lesbare Sicherung. Ist die neueste selbst beschädigt, die davor.
5. Gesichert wird nur, wenn sich die Show seit der letzten Sicherung geändert hat.

Das **Journal** wird zurückgestellt, bis Undo/Redo oder große Shows es erfordern (ab Phase 5).

## Begründung

- Eine vollständige Sicherung ist einfacher und fehlerärmer als ein Journal mit Wiederabspielen, und sie nutzt den vorhandenen, getesteten Code.
- Der mögliche Verlust ist durch das Sicherungsintervall begrenzt (wenige Sekunden).
- Undo und Redo gehören in den Master und brauchen später ohnehin Änderungsereignisse. Ein Journal lässt sich dann mit mehr Wissen entwerfen.

## Verworfene Alternativen

- **Journal sofort:** Mehr Aufwand (Format, Prüfsummen, Wiederabspielen, Aufräumen) für einen Vorteil, der bei kleinen Shows nicht gebraucht wird.
- **Nur manuelles Speichern:** Zu riskant bei Live-Shows.

## Offene Punkte

- Intervall (Vorschlag: einige Sekunden) und Anzahl N der behaltenen Sicherungen (Vorschlag: etwa zehn).
- Ob nach einem manuellen Speichern die Sicherungen gelöscht werden.
- Bereinigung des Show-Namens für den Dateinamen (Schrägstriche und andere unerlaubte Zeichen).
- Hinweis in der Oberfläche, wenn eine Sicherung neuer ist als die Hauptdatei.
- Wann das Journal nachgerüstet wird.

## Konsequenzen

- Der Master schickt dem Interpreter regelmäßig einen Snapshot (siehe Architektur) oder meldet, dass sich etwas geändert hat. Er schreibt selbst nie auf die Platte.
- ADR 0005 verweist für das Journal auf eine spätere ADR.
