// Implements the loopback TCP server and its poll-based shutdown wake-up.

#include "dmx/include/link/link_server.h"

#include <utility>
#include <netinet/in.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <unistd.h>

#include "dmx/include/link/link_session.h"


namespace archx::dmx::link {

LinkServer::LinkServer(FrameSink& sink, PayloadParser parser)
    : sink_(sink), parser_(std::move(parser)) {}

LinkServer::~LinkServer() { stop(); }

bool LinkServer::start(std::uint16_t port) {
  if (running_.load()) {
    return false;
  }

  listenFd_.reset(socket(AF_INET, SOCK_STREAM, 0));
  if (listenFd_.get() < 0) {
    return false;
  }

  int reuse = 1;
  if (setsockopt(listenFd_.get(), SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&reuse), sizeof(reuse)) == -1) {
    listenFd_.reset();
    return false;
  }

  sockaddr_in address{};
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

  if (bind(listenFd_.get(), reinterpret_cast<const sockaddr*>(&address), sizeof(address)) == -1) {
    listenFd_.reset();
    return false;
  }

  if (listen(listenFd_.get(), SOMAXCONN) == -1) {
    listenFd_.reset();
    return false;
  }

  sockaddr_in boundaddress{};
  socklen_t boundaddress_length = sizeof(boundaddress);

  if (getsockname(listenFd_.get(), reinterpret_cast<sockaddr*>(&boundaddress), &boundaddress_length) == -1) {
    listenFd_.reset();
    return false;
  }

  port_ = ntohs(boundaddress.sin_port);

  int pipeFds[2];
  if (pipe(pipeFds) == -1) {
    listenFd_.reset();
    port_ = 0;
    return false;
  }

  wakeRead_.reset(pipeFds[0]);
  wakeWrite_.reset(pipeFds[1]);

  running_.store(true);
  thread_ = std::thread(&LinkServer::run, this);

  return true;
}

void LinkServer::stop() {
  if (running_.load()) {
    running_.store(false);

    if (wakeWrite_.get() >= 0) {
      const std::uint8_t byte = 1;
      (void)write(wakeWrite_.get(), &byte, 1);
    }

    if (thread_.joinable()) {
      thread_.join();
    }

    wakeRead_.reset();
    wakeWrite_.reset();
    listenFd_.reset();
    port_ = 0;
  }
}

void LinkServer::run() {
  LinkSession session(sink_, parser_);
  UniqueFd clientFd;
  while (running_.load()) {
    pollfd fds[3];
    nfds_t nfds = 0;

    fds[nfds++] = {wakeRead_.get(), POLLIN, 0};
    fds[nfds++] = {listenFd_.get(), POLLIN, 0};
    if (clientFd.get() >= 0) {
      fds[nfds++] = {clientFd.get(), POLLIN, 0};
    }

    const int ready = poll(fds, nfds, -1);
    if (ready < 0) {

      if (errno == EINTR) {
        continue;
      }
      break;
    }
    if (fds[0].revents & POLLIN) {
      std::fprintf(stderr, "break: Wecker\n");
      break;
    }

    if (fds[1].revents & POLLIN) {
      const int fd = accept(listenFd_.get(), nullptr, nullptr);
      if (fd >= 0) {
        clientFd.reset(fd);
        session.reset();
        ++accepted_;
      }
      continue;
    }
    if (nfds == 3 && (fds[2].revents & (POLLIN | POLLHUP | POLLERR))) {
      std::uint8_t buffer[4096];
      const ssize_t n = recv(clientFd.get(), buffer, sizeof(buffer), 0);

      if (n > 0) {
        if (!session.onBytes(buffer, static_cast<std::size_t>(n))) {
          clientFd.reset();
        }
      } else if (n == 0) {
        clientFd.reset();
      } else if (errno != EINTR) {
        clientFd.reset();
      }
    }
  }
}

}  // namespace archx::dmx::link
