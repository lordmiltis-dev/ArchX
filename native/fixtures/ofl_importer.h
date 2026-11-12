#pragma once

#include <string>

#include "showfile.pb.h"  // entsteht beim Bauen aus schema/showfile.proto

namespace archx {

// Baut aus dem Text einer OFL-Fixture-Datei (JSON) den FixtureType für EINEN Modus.
//
//   json_text          der komplette Inhalt der OFL-Datei
//   manufacturer_name  Anzeigename des Herstellers (steht in OFL in einer eigenen Datei, deshalb übergibt ihn der Aufrufer)
//   mode_name          Name des Modus, zum Beispiel "6-channel"
//
// Die ID des Typs bleibt leer, sie vergibt später der Master.
// Wirft std::runtime_error, wenn der Modus nicht existiert oder etwas nicht unterstützt wird.
FixtureType ImportOflMode(const std::string& json_text,
                          const std::string& manufacturer_name,
                          const std::string& mode_name);

}  // namespace archx
