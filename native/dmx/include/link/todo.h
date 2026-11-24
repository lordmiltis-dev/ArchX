#pragma once
// Hilfe: Platzhalter für noch nicht implementierte Funktionen.
// Die Tests melden eine NotImplemented-Ausnahme als "TODO" (nicht als Absturz).
#include <stdexcept>
#include <string>

namespace archx {
class NotImplemented : public std::logic_error {
 public:
  explicit NotImplemented(const std::string& what) : std::logic_error(what) {}
};
}  // namespace archx

#define ARCHX_TODO(msg) throw ::archx::NotImplemented(msg)
