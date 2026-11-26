#include "dmx/include/link/link_session.h"

#include <algorithm>
#include <utility>

#include "dmx/include/link/todo.h"

namespace archx::dmx::link {

LinkSession::LinkSession(FrameSink& sink, PayloadParser parser)
    : sink_(sink), parser_(std::move(parser)) {}

bool LinkSession::onBytes(const std::uint8_t* data, std::size_t size) {
  // TODO: 1. Ist decoder_ schon failed(): return false (nicht erneut zählen).
  //       2. decoder_.feed(...) in einen lokalen Vektor.
  //       3. JEDE fertige Payload der Reihe nach an handlePayload() geben
  //          (auch wenn feed() false lieferte: Rahmen vor dem Fehler zählen).
  //       4. Bei false: ++stats_.framingErrors, return false. Sonst true.
  (void)data;
  (void)size;
  ARCHX_TODO("LinkSession::onBytes");
}

void LinkSession::handlePayload(const std::vector<std::uint8_t>& payload) {
  // TODO: parser_ aufrufen.
  //  - nullopt                                  -> ++stats_.framesMalformed
  //  - universe > kMaxUniverse oder data.size() != kUniverseSize
  //                                             -> ++stats_.framesInvalid
  //  - sonst in UniverseData kopieren, sink_.setFrame(...), ++framesApplied
  (void)payload;
  (void)sink_;
  ARCHX_TODO("LinkSession::handlePayload");
}

void LinkSession::reset() {
  // TODO: decoder_.reset(); Statistik und Sink bleiben unberührt.

  decoder_.reset();
}

}  // namespace archx::dmx::link
