#include "dmx/include/link/frame_codec.h"

#include <cstring>
#include <stdexcept>
#include <algorithm>

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

  if (payload == nullptr && size > 0) {
    throw std::invalid_argument("Invalid frame payload");
  }

  std::vector<std::uint8_t> frame;
  frame.reserve(size + 4);

  for (std::size_t i = 0; i < 4; ++i) {
    frame.push_back(static_cast<std::uint8_t>((size >> (8 * i)) & 0xFF));
  }

  frame.insert(frame.end(), payload, payload + size);
  return frame;
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
  if (failed_) {
    return false;
  }

  std::size_t index = 0;

  while (index < size) {
    if (!inPayload_) {
      const std::size_t needed = kHeaderSize - headerBytes_;
      const std::size_t available = std::min(needed, size - index);

      std::memcpy(header_ + headerBytes_, data + index, available);

      headerBytes_ += available;
      index += available;

      if (headerBytes_ < kHeaderSize) {
        continue;
      }

      std::uint32_t length = 0;
      for (std::size_t i = 0; i < kHeaderSize; ++i) {
        length |= static_cast<std::uint32_t>(header_[i]) << (8 * i);
      }

      if (length == 0 || length > kMaxPayloadSize) {
        failed_ = true;
        return false;
      }

      expected_ = length;
      payload_.reserve(length);
      inPayload_ = true;
    }

    const std::size_t remaining = expected_ - payload_.size();
    const std::size_t available = std::min(remaining, size - index);

    payload_.insert(payload_.end(), data + index, data + available + index);

    index += available;

    if (payload_.size() == expected_) {
      out.push_back(std::move(payload_));
      payload_.clear();

      headerBytes_ = 0;
      expected_ = 0;
      inPayload_ = false;
    }
  }
  return true;
}

std::size_t FrameDecoder::bufferedBytes() const {
  // TODO: inPayload_ ? kHeaderSize + payload_.size() : headerBytes_
  if (inPayload_) {
    return kHeaderSize + payload_.size();
  }
  return headerBytes_;
}

void FrameDecoder::reset() {
  // TODO: alle Zustandsvariablen auf Anfang (auch failed_ = false).
  failed_ = false;
  headerBytes_ = 0;
  expected_ = 0;
  inPayload_ = false;
  payload_.clear();

}

}  // namespace archx::dmx::link
