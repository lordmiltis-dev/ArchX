#include "dmx/include/link/link_server.h"

#include <utility>

#include "dmx/include/link/todo.h"

namespace archx::dmx::link {

LinkServer::LinkServer(FrameSink& sink, PayloadParser parser)
    : sink_(sink), parser_(std::move(parser)) {}

LinkServer::~LinkServer() { stop(); }

bool LinkServer::start(std::uint16_t port) {
  // TODO (Reihenfolge):
  //  1. running_ schon true -> false.
  //  2. socket(AF_INET, SOCK_STREAM), SO_REUSEADDR, bind auf
  //     htonl(INADDR_LOOPBACK) (NICHT INADDR_ANY!), listen.
  //  3. getsockname -> port_ setzen (wichtig für port == 0).
  //  4. pipe() für den Wecker anlegen, running_ = true, thread_ = std::thread(run).
  //  5. Bei jedem Fehler: alles wieder schließen, false zurückgeben (kein Leck).
  (void)port;
  ARCHX_TODO("LinkServer::start");
}

void LinkServer::stop() {
  // TODO: Darf NICHT werfen (Destruktor). Nicht gestartet -> nichts tun.
  //       running_ auf false, ein Byte in wakeWrite_ schreiben, thread_ joinen,
  //       danach die Deskriptoren schließen (UniqueFd::reset()).
  //       Muss mehrfach aufrufbar sein.
}

void LinkServer::run() {
  // TODO: Schleife mit poll() über [wakeRead_, listenFd_, aktiver Client]:
  //  - Wecker lesbar            -> Schleife beenden
  //  - listenFd_ lesbar         -> accept(); alten Client schließen (ersetzen),
  //                                session.reset(), ++accepted_
  //  - Client lesbar            -> recv(); 0 oder Fehler -> Client schließen;
  //                                sonst session.onBytes(); false -> schließen
  //                                (EINTR ist kein Fehler)
  //  Eine LinkSession (sink_, parser_) lebt in dieser Funktion.
  (void)sink_;
  (void)parser_;
  (void)accepted_;
}

}  // namespace archx::dmx::link
