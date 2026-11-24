#pragma once
#include <cstddef>
#include <cstdint>

#include "dmx/include/link/dmx_frame_proto.h"
#include "dmx/include/link/frame_codec.h"
#include "dmx/include/link/frame_sink.h"

namespace archx::dmx::link {

struct LinkStats {
  std::uint64_t framesApplied = 0;    // an den Sink weitergegeben
  std::uint64_t framesMalformed = 0;  // Payload nicht als DmxFrame lesbar
  std::uint64_t framesInvalid = 0;    // lesbar, aber Universum > kMaxUniverse oder data != 512 Bytes
  std::uint64_t framingErrors = 0;    // Protokollfehler im Datenstrom (je Fehler einmal)
};

// Logik pro Verbindung, ohne Sockets (dadurch ohne Netzwerk testbar):
// Bytes -> Rahmen -> DmxFrame -> prüfen -> Sink.
//
// Regeln:
//  - Rahmen mit ungültigem INHALT (malformed/invalid) werden gezählt und
//    übersprungen. Der Strom bleibt synchron, die Verbindung bleibt offen.
//  - Ein Fehler im FRAMING beendet die Verbindung: onBytes() gibt false zurück.
//    Rahmen vor dem Fehler (auch im selben Aufruf) wurden schon angewendet.
//    Weitere Bytes werden bis reset() ignoriert (false, ohne neu zu zählen).
//  - Reihenfolge der Rahmen bleibt erhalten.
class LinkSession {
 public:
  explicit LinkSession(FrameSink& sink, PayloadParser parser = parseDmxFrame);

  bool onBytes(const std::uint8_t* data, std::size_t size);

  // Neue Verbindung: angefangene Daten und Fehlerzustand weg, Statistik bleibt,
  // der Sink wird NICHT angefasst (die letzten Rahmen bleiben aktiv).
  void reset();

  const LinkStats& stats() const { return stats_; }

 private:
  void handlePayload(const std::vector<std::uint8_t>& payload);

  FrameSink& sink_;
  PayloadParser parser_;
  FrameDecoder decoder_;
  LinkStats stats_;
};

}  // namespace archx::dmx::link
