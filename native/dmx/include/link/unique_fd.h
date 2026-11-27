// Provides a small RAII wrapper for file descriptors such as sockets and pipes.

#pragma once
#include <unistd.h>

#include <utility>

namespace archx::dmx::link {

class UniqueFd {
 public:
  UniqueFd() = default;
  explicit UniqueFd(int fd) : fd_(fd) {}
  ~UniqueFd() { reset(); }
  UniqueFd(const UniqueFd&) = delete;
  UniqueFd& operator=(const UniqueFd&) = delete;
  UniqueFd(UniqueFd&& o) noexcept : fd_(o.release()) {}
  UniqueFd& operator=(UniqueFd&& o) noexcept {
    if (this != &o) reset(o.release());
    return *this;
  }

  int get() const { return fd_; }
  explicit operator bool() const { return fd_ >= 0; }
  int release() { return std::exchange(fd_, -1); }
  void reset(int fd = -1) {
    if (fd_ >= 0) ::close(fd_);
    fd_ = fd;
  }

 private:
  int fd_ = -1;
};

}  // namespace archx::dmx::link
