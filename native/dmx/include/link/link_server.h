#pragma once
#include <atomic>
#include <cstdint>
#include <thread>

#include "dmx/include/link/dmx_frame_proto.h"
#include "dmx/include/link/frame_sink.h"
#include "dmx/include/link/unique_fd.h"

namespace archx::dmx::link {

// TCP-Server für die Master-Verbindung (POSIX-Sockets; Linux/WSL2).
//
// Regeln (jede wird von tests/link/test_link_server.cpp geprüft):
//  - Lauscht NUR auf 127.0.0.1, nie auf 0.0.0.0.
//  - Höchstens eine aktive Verbindung. Kommt eine neue, wird die alte
//    geschlossen und die neue übernimmt (Master wurde neu gestartet, die alte
//    Verbindung kann halb offen sein).
//  - Abbruch einer Verbindung (Client schließt, Protokollfehler) löscht NICHT
//    die letzten Rahmen im Sink: die Ausgabe läuft weiter.
//  - Ein angefangener Rahmen einer abgebrochenen Verbindung wird verworfen.
//  - Protokollfehler: nur diese Verbindung wird geschlossen, der Server läuft
//    weiter. Ungültiger Inhalt schließt die Verbindung nicht.
//  - stop() kehrt auch bei blockierten Lesevorgängen zügig zurück (< 1 s).
class LinkServer {
 public:
  explicit LinkServer(FrameSink& sink, PayloadParser parser = parseDmxFrame);
  ~LinkServer();  // ruft stop()
  LinkServer(const LinkServer&) = delete;
  LinkServer& operator=(const LinkServer&) = delete;

  // port == 0: das Betriebssystem wählt einen freien Port (für Tests).
  // false, wenn schon gestartet oder Port belegt/nicht bindbar.
  bool start(std::uint16_t port);

  // Der tatsächlich gebundene Port (0, solange nicht gestartet).
  std::uint16_t port() const { return port_; }

  // Zählt jede angenommene Verbindung.
  std::uint64_t connectionsAccepted() const { return accepted_.load(); }

  // Idempotent; auch vor start() und im Destruktor erlaubt (darf nicht werfen).
  void stop();

 private:
  void run();  // Netzwerk-Thread: akzeptieren, lesen, an LinkSession geben

  FrameSink& sink_;
  PayloadParser parser_;
  std::atomic<std::uint64_t> accepted_{0};
  std::uint16_t port_ = 0;
  std::atomic<bool> running_{false};
  std::thread thread_;
  // Vorschlag: poll() auf [Wecker, Listen-Socket, aktiver Client]. stop()
  // schreibt ein Byte in die Pipe und weckt den Thread, statt auf ein
  // blockierendes accept()/recv() zu warten.
  UniqueFd listenFd_;
  UniqueFd wakeRead_;
  UniqueFd wakeWrite_;
};

}  // namespace archx::dmx::link
