#pragma once
#include <cstddef>
#include <cstdint>
#include <vector>

namespace archx::dmx::link {

// Rahmen auf dem Draht: [4 Bytes Länge, little-endian, vorzeichenlos][Payload].
// Gültige Länge: 1 .. kMaxPayloadSize. Länge 0 und größere Werte sind ein
// Protokollfehler (der Datenstrom ist dann nicht mehr zu retten).
inline constexpr std::size_t kHeaderSize = 4;
inline constexpr std::uint32_t kMaxPayloadSize = 65536;

// Baut Header + Payload.
// Wirft std::invalid_argument, wenn size == 0 oder size > kMaxPayloadSize.
std::vector<std::uint8_t> encodeFrame(const std::uint8_t* payload, std::size_t size);
inline std::vector<std::uint8_t> encodeFrame(const std::vector<std::uint8_t>& payload) {
  return encodeFrame(payload.data(), payload.size());
}

// Setzt aus einem TCP-Datenstrom wieder Rahmen zusammen. TCP liefert Bytes in
// beliebigen Stücken: ein Rahmen kann auf mehrere feed()-Aufrufe verteilt sein,
// ein Aufruf kann mehrere Rahmen enthalten.
class FrameDecoder {
 public:
  // Fügt fertige Payloads an `out` an (bestehender Inhalt von `out` bleibt).
  // Rückgabe false = Protokollfehler (Länge 0 oder > kMaxPayloadSize).
  //  - Rahmen, die VOR dem Fehler vollständig waren, stehen trotzdem in `out`.
  //  - Der Fehler wird schon erkannt, sobald der Header da ist (nicht erst nach
  //    dem Payload) und es wird nie ein Puffer in der angekündigten Größe
  //    angelegt, wenn die Länge ungültig ist.
  //  - Danach gilt failed() == true; weitere Aufrufe geben false zurück, ändern
  //    `out` nicht und verbrauchen nichts. Nur reset() hebt das auf.
  bool feed(const std::uint8_t* data, std::size_t size,
            std::vector<std::vector<std::uint8_t>>& out);

  bool failed() const { return failed_; }

  // Bytes eines angefangenen, noch unvollständigen Rahmens (Header-Bytes
  // eingerechnet). 0, wenn genau an einer Rahmengrenze.
  std::size_t bufferedBytes() const;

  // Verwirft angefangene Daten und den Fehlerzustand (neue Verbindung).
  void reset();

 private:
  // TODO: Zustand für den Zusammenbau (Vorschlag: Header-Puffer + Zähler,
  //       erwartete Länge, bisher gesammelter Payload).
  bool failed_ = false;
  std::uint8_t header_[kHeaderSize] = {};
  std::size_t headerBytes_ = 0;
  std::size_t expected_ = 0;
  bool inPayload_ = false;
  std::vector<std::uint8_t> payload_;
};

}  // namespace archx::dmx::link
