# ADR 0004: Fremde Inhalte (Fixture-Libraries, DyLOS-Content)

- **Status:** Vorläufig
- **Datum:** 2026-10-20

## Kontext

ArchX soll die Onyx-Fixture-Library und DyLOS-Content nutzen können, aber nicht von Onyx abhängig sein. Diese Inhalte gehören Dritten:

- **DyLOS-Content:** wird von Obsidian als separates Paket zum Download angeboten und in Onyx importiert. Die Pakete sind groß (bei Onyx 4.4 bis etwa 9 GB).
- **Fixture-Library:** Die Daten stammen seit Onyx 4.6 von AtlaBase. Dahinter stehen die Daten der Hersteller. Die Library wird täglich aktualisiert.

Die Lizenzbedingungen dieser Downloads wurden bisher **nicht geprüft**.

Aus dem Wunsch nach Bequemlichkeit entstand die Idee, im Installations-Wizard eine Checkbox anzubieten, die Library und Content automatisch herunterlädt.

## Entscheidung

1. **Keine fremden Inhalte im Repository und im ArchXOs-Image.** Sie liegen beim Nutzer (im Repo ausgeschlossen über `/local-data/` in der `.gitignore`).
2. **Standardweg im Wizard:** Ein Schritt "Inhalte" öffnet die offizielle Download-Seite und führt durch den Import, oder der Nutzer wählt eine bereits heruntergeladene Datei aus.
3. **Automatischer Download** nur als eigene **Importer-Extension**, standardmäßig ausgeschaltet, mit Hinweis auf die Bedingungen des Herstellers und **nur nach Klärung bzw. Erlaubnis** durch Obsidian und AtlaBase. ArchX selbst hostet und verteilt nichts.
4. **Rechtlich unkomplizierte Quellen** (offene Fixture-Datenbanken, GDTF) können nach Prüfung ihrer Lizenz als Standard angeboten werden.

## Begründung

- Ob eine Drittsoftware die Download-Adressen eines Herstellers automatisch ansteuern darf, regeln dessen Nutzungsbedingungen. Ohne Erlaubnis kann das als unerwünschter Zugriff gelten.
- Adressen und Formate können sich jederzeit ändern. Ein fest verdrahteter Download würde dann ohne Zutun brechen.
- ArchX soll unabhängig von Onyx bleiben (siehe auch ADR 0002).
- Mit einer offenen Lizenz und einem späteren kommerziellen Angebot sollen keine Streitfragen über Inhalte Dritter entstehen.

## Verworfene Alternativen

- **Fest eingebauter automatischer Download ohne Erlaubnis:** rechtliches und technisches Risiko.
- **Inhalte mit ArchX oder dem Image ausliefern:** Weiterverbreitung fremder Inhalte.

## Offene Punkte

- Anfrage bei Obsidian und AtlaBase, ob ein automatischer Abruf erlaubt ist oder eine offizielle Schnittstelle existiert.
- Lizenzbedingungen der Downloads lesen.
- Lizenzen der Open Fixture Library und von GDTF prüfen, bevor sie gebündelt oder automatisch geladen werden.
- Technische Anforderungen für große Pakete (Fortschrittsanzeige, Wiederaufnahme nach Abbruch, Prüfsumme) in Phase 6 und 10.

## Konsequenzen

- Der Installer bleibt klein, Inhalte lassen sich nachladen.
- Importer für Fixture-Libraries und Pixel-Mapping-Inhalte sind Extension-Typen (siehe [Architektur](../architecture.md)).
