#include "dmx/include/link/frame_codec.h"

#include <stdexcept>

#include "dmx/include/link/todo.h"

namespace archx::dmx::link {

std::vector<std::uint8_t> encodeFrame(const std::uint8_t* payload, std::size_t size) {
  // TODO: size == 0 oder size > kMaxPayloadSize -> std::invalid_argument.
  // TODO: 4 Bytes Länge, NIEDRIGSTES Byte zuerst, danach die Payload.
  //       Tipp: schreibe die Bytes einzeln mit Schieben (size >> (8 * i)) & 0xFF,
  //       dann hängt es nicht von der Byte-Reihenfolge des Rechners ab.

  if (size == 0 || size > kMaxPayloadSize) {
    throw std::invalid_argument("Invalid frame size");
  }
}

bool FrameDecoder::feed(const std::uint8_t* data, std::size_t size,
                        std::vector<std::vector<std::uint8_t>>& out) {
  // TODO: Zustandsautomat mit zwei Zuständen: Header sammeln / Payload sammeln.
  //  - Header kann auf mehrere Aufrufe verteilt sein (auch 1 Byte pro Aufruf).
  //  - Sobald 4 Header-Bytes da sind: Länge prüfen (0 oder > kMaxPayloadSize
  //    -> failed_ = true, return false) BEVOR Speicher reserviert wird.
  //  - Fertigen Payload nach `out` verschieben, danach wieder Header sammeln.
  //  - Ein Aufruf kann mehrere Rahmen enthalten -> Schleife über die Eingabe.
  //  - Ist failed_ schon gesetzt: sofort false, `out` unverändert.
  (void)data;
  (void)size;
  (void)out;
  ARCHX_TODO("FrameDecoder::feed");
}

std::size_t FrameDecoder::bufferedBytes() const {
  // TODO: inPayload_ ? kHeaderSize + payload_.size() : headerBytes_
  ARCHX_TODO("FrameDecoder::bufferedBytes");
}

void FrameDecoder::reset() {
  // TODO: alle Zustandsvariablen auf Anfang (auch failed_ = false).
  ARCHX_TODO("FrameDecoder::reset");
}

}  // namespace archx::dmx::link
