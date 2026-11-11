#include "ofl_importer.h"

#include <map>
#include <stdexcept>
#include <string>

#include <nlohmann/json.hpp>

namespace archx {

namespace {

// Wandelt eine OFL-Fähigkeit (capability) in unser Attribut um.
// `capability` ist ein JSON-Objekt wie {"type": "ColorIntensity", "color": "Red"}.
// Die Tabelle steht in docs/arbeitsplan-tag-1-und-2.md.
Attribute AttributeFor(const nlohmann::json& capability) {
    // TODO: capability.at("type") lesen und per Tabelle zuordnen.
    //       Bei "ColorIntensity" entscheidet zusätzlich capability.at("color").
    //       Unbekanntes wird ATTRIBUTE_OTHER.
    (void)capability;
    return ATTRIBUTE_OTHER;
}

}  // namespace

FixtureType ImportOflMode(const std::string& json_text,
                          const std::string& manufacturer_name,
                          const std::string& mode_name) {
    // Schritte (Einzelheiten im Arbeitsplan):
    //
    //  1. JSON einlesen:                 auto fixture = nlohmann::json::parse(json_text);
    //  2. Den Modus mit passendem Namen in fixture.at("modes") suchen, sonst std::runtime_error werfen.
    //  3. Position jedes Kanalnamens bestimmen (Erster Durchlauf): Durch mode.at("channels") gehen,
    //     die Position zählt ab 1. Ein Eintrag null ist ein unbenutzter Kanal: Er zählt mit, bekommt aber keinen Eintrag.
    //     Ein Eintrag, der ein Objekt ist (Matrix-Kanäle), ist noch nicht unterstützt: std::runtime_error werfen.
    //  4. Feinkanäle erkennen: Hat ein Kanal in fixture.at("availableChannels") das Feld "fineChannelAliases",
    //     ist der erste Alias der Feinkanal. Er bekommt keinen eigenen ChannelDef, seine Position wird
    //     fine_offset des Hauptkanals.
    //  5. ChannelDefs bauen (Zweiter Durchlauf): offset, attribute (über AttributeFor), name,
    //     highlight_value (Feld "highlightValue", wenn es eine Zahl ist),
    //     precedence (Feld "precedence": "HTP" oder "LTP"), fine_offset.
    //     Die Fähigkeit steht entweder in "capability" (ein Objekt) oder in "capabilities" (eine Liste).
    //     Bei einer Liste zählt die erste, die nicht "NoFunction" ist.
    //  6. FixtureType füllen: name, manufacturer, mode_name, library_source ("ofl"),
    //     library_key ("<manufacturerKey>/<fixtureKey>/<Modusname>"), library_revision (meta.lastModifyDate),
    //     channel_count (Anzahl der Einträge in mode.at("channels"), auch der unbenutzten).
    (void)json_text;
    (void)manufacturer_name;
    (void)mode_name;
    throw std::runtime_error("ImportOflMode ist noch nicht umgesetzt");
}

}  // namespace archx
