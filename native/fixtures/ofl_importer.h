#pragma once

#include <string>

#include "showfile.pb.h"  // generated during the build from schema/showfile.proto

namespace archx {

// Build a FixtureType for a single mode from the JSON text of an OFL fixture file.
//
//   json_text          the full content of the OFL file
//   manufacturer_name  display name of the manufacturer (stored in a separate OFL file, so it is passed by the caller)
//   mode_name          name of the mode, for example "6-channel"
//
// The type ID remains empty; the master assigns it later.
// Throws std::runtime_error if the mode does not exist or if something is unsupported.
FixtureType ImportOflMode(const std::string& json_text,
                          const std::string& manufacturer_name,
                          const std::string& mode_name);

}  // namespace archx
