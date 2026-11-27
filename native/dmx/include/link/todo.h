// Provides a test-visible exception for functions that are not implemented yet.
// The test runner reports unimplemented cases instead of allowing them to crash.

#pragma once
#include <stdexcept>
#include <string>

namespace archx {
class NotImplemented : public std::logic_error {
 public:
  explicit NotImplemented(const std::string& what) : std::logic_error(what) {}
};
}  // namespace archx

#define ARCHX_TODO(msg) throw ::archx::NotImplemented(msg)
