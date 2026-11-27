// Declares the loopback-only TCP server that receives framed DMX data.

#pragma once
#include <atomic>
#include <cstdint>
#include <thread>

#include "dmx/include/link/dmx_frame_proto.h"
#include "dmx/include/link/frame_sink.h"
#include "dmx/include/link/unique_fd.h"

namespace archx::dmx::link {

// TCP server for the master connection (POSIX sockets; Linux/WSL2).
//
// The server listens only on 127.0.0.1 and keeps at most one active client.
// A new client replaces the old one. Disconnects discard partial frames but
// preserve the sink's latest output; framing errors close only that connection,
// while invalid payload contents do not. stop() returns promptly even when the
// network thread is waiting for input.
class LinkServer {
 public:
  explicit LinkServer(FrameSink& sink, PayloadParser parser = parseDmxFrame);
  ~LinkServer();  // Calls stop().
  LinkServer(const LinkServer&) = delete;
  LinkServer& operator=(const LinkServer&) = delete;

  // If port is zero, the operating system selects an available port.
  // Returns false if already started or if the port cannot be bound.
  bool start(std::uint16_t port);

  // The actual bound port; zero before the server starts.
  std::uint16_t port() const { return port_; }

  // Number of accepted connections.
  std::uint64_t connectionsAccepted() const { return accepted_.load(); }

  // Idempotent; safe before start() and from the destructor. Does not throw.
  void stop();

 private:
  void run();  // Accepts and reads clients, forwarding bytes to LinkSession.

  FrameSink& sink_;
  PayloadParser parser_;
  std::atomic<std::uint64_t> accepted_{0};
  std::uint16_t port_ = 0;
  std::atomic<bool> running_{false};
  std::thread thread_;
  // poll() watches the wake pipe, listening socket, and active client.
  // stop() writes to the pipe to wake the network thread.
  UniqueFd listenFd_;
  UniqueFd wakeRead_;
  UniqueFd wakeWrite_;
};

}  // namespace archx::dmx::link
