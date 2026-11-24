#pragma once
// Hilfe: Mini-Testrahmen ohne Abhängigkeiten + Werkzeuge für die Link-Tests.
// Absichtlich klein gehalten; lässt sich später gegen GoogleTest/Catch2
// austauschen, die TEST/CHECK-Namen sind dafür ähnlich.

#include <arpa/inet.h>
#include <netinet/in.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <functional>
#include <iostream>
#include <mutex>
#include <optional>
#include <sstream>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "dmx/include/link/dmx_frame_proto.h"
#include "dmx/include/link/frame_sink.h"
#include "dmx/include/link/todo.h"

#ifndef ARCHX_VECTORS_PATH
#error "ARCHX_VECTORS_PATH muss auf schema/testdata/dmx_link_vectors.txt zeigen (siehe CMake/run_link_tests.sh)"
#endif

namespace archx::test {

using Bytes = std::vector<std::uint8_t>;

// ---------------------------------------------------------------- Rahmen
struct CheckFailure : std::runtime_error {
  using std::runtime_error::runtime_error;
};
struct TestCase {
  const char* name;
  void (*fn)();
};
inline std::vector<TestCase>& registry() {
  static std::vector<TestCase> r;
  return r;
}
struct Registrar {
  Registrar(const char* n, void (*f)()) { registry().push_back({n, f}); }
};

inline std::string toHex(const Bytes& b, std::size_t maxBytes = 24) {
  static const char* d = "0123456789abcdef";
  std::string s;
  for (std::size_t i = 0; i < b.size() && i < maxBytes; ++i) {
    s += d[b[i] >> 4];
    s += d[b[i] & 15];
  }
  if (b.size() > maxBytes) s += "..(" + std::to_string(b.size()) + " Bytes)";
  return s.empty() ? "(leer)" : s;
}
inline Bytes fromHex(const std::string& h) {
  Bytes b;
  for (std::size_t i = 0; i + 1 < h.size(); i += 2)
    b.push_back(static_cast<std::uint8_t>(std::stoi(h.substr(i, 2), nullptr, 16)));
  return b;
}

template <class T>
std::string show(const T& v) {
  if constexpr (std::is_same_v<T, Bytes>) {
    return toHex(v);
  } else if constexpr (std::is_enum_v<T>) {
    return std::to_string(static_cast<long long>(v));
  } else {
    std::ostringstream o;
    o << v;
    return o.str();
  }
}

#define TEST(name)                                                  \
  static void name();                                               \
  static ::archx::test::Registrar registrar_##name(#name, &name);   \
  static void name()

#define ARCHX_FAIL_AT(msg) \
  throw ::archx::test::CheckFailure(std::string(__FILE__) + ":" + std::to_string(__LINE__) + ": " + (msg))

#define CHECK(cond) \
  do { if (!(cond)) ARCHX_FAIL_AT("CHECK(" #cond ")"); } while (0)

#define CHECK_EQ(a, b)                                                                   \
  do {                                                                                   \
    auto&& va_ = (a);                                                                    \
    auto&& vb_ = (b);                                                                    \
    if (!(va_ == vb_))                                                                   \
      ARCHX_FAIL_AT(std::string("CHECK_EQ(" #a ", " #b "): ") + ::archx::test::show(va_) + \
                    " != " + ::archx::test::show(vb_));                                  \
  } while (0)

#define CHECK_THROWS(expr, ExcType)                                              \
  do {                                                                           \
    bool thrown_ = false;                                                        \
    try { (void)(expr); } catch (const ExcType&) { thrown_ = true; }              \
    if (!thrown_) ARCHX_FAIL_AT("erwartet " #ExcType " bei " #expr);             \
  } while (0)

inline int runAll() {
  int failed = 0, todo = 0, passed = 0;
  for (auto& t : registry()) {
    try {
      t.fn();
      ++passed;
      std::printf("  ok    %s\n", t.name);
    } catch (const archx::NotImplemented& e) {
      ++todo;
      ++failed;
      std::printf("  TODO  %s  (%s)\n", t.name, e.what());
    } catch (const std::exception& e) {
      ++failed;
      std::printf("  FAIL  %s\n        %s\n", t.name, e.what());
    }
  }
  std::printf("%d ok, %d fehlgeschlagen (davon %d noch TODO)\n", passed, failed, todo);
  return failed == 0 ? 0 : 1;
}
#define ARCHX_TEST_MAIN() \
  int main() { return ::archx::test::runAll(); }

// ---------------------------------------------------------------- Zeit
inline bool waitFor(const std::function<bool()>& pred, int timeoutMs = 2000) {
  auto end = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
  while (std::chrono::steady_clock::now() < end) {
    if (pred()) return true;
    std::this_thread::sleep_for(std::chrono::milliseconds(2));
  }
  return pred();
}
inline void sleepMs(int ms) { std::this_thread::sleep_for(std::chrono::milliseconds(ms)); }

// ---------------------------------------------------------------- Referenzwerte
struct FrameVector {
  std::string name;
  Bytes payload, framed;
};
struct ProtoVector {
  std::string name;
  std::uint32_t universe;
  std::uint8_t fill;
  Bytes wire;
};
struct Vectors {
  std::vector<FrameVector> frames;
  std::vector<ProtoVector> protos;
};
inline const Vectors& vectors() {
  static Vectors v = [] {
    Vectors out;
    std::ifstream in(ARCHX_VECTORS_PATH);
    if (!in) throw std::runtime_error(std::string("Referenzdatei nicht lesbar: ") + ARCHX_VECTORS_PATH);
    std::string line;
    while (std::getline(in, line)) {
      if (line.empty() || line[0] == '#') continue;
      std::vector<std::string> f;
      std::stringstream ss(line);
      std::string part;
      while (std::getline(ss, part, '|')) f.push_back(part);
      if (f[0] == "frame" && f.size() == 4) {
        out.frames.push_back({f[1], fromHex(f[2]), fromHex(f[3])});
      } else if (f[0] == "proto" && f.size() == 5) {
        out.protos.push_back({f[1], static_cast<std::uint32_t>(std::stoul(f[2])),
                              static_cast<std::uint8_t>(std::stoi(f[3], nullptr, 16)), fromHex(f[4])});
      }
    }
    if (out.frames.empty() || out.protos.empty()) throw std::runtime_error("Referenzdatei leer/unlesbar");
    return out;
  }();
  return v;
}

// ---------------------------------------------------------------- unabhängige Test-Protobuf-Hilfe
// Bewusst NICHT der Produktionscode: damit prüfen die Session-/Server-Tests ihre
// Logik, ohne vom generierten Protobuf-Code abzuhängen.
inline void putVarint(Bytes& o, std::uint64_t v) {
  while (v >= 0x80) { o.push_back(static_cast<std::uint8_t>(v | 0x80)); v >>= 7; }
  o.push_back(static_cast<std::uint8_t>(v));
}
inline Bytes wireFrame(std::uint32_t universe, const Bytes& data) {
  Bytes o;
  if (universe != 0) { o.push_back(0x08); putVarint(o, universe); }
  if (!data.empty()) { o.push_back(0x12); putVarint(o, data.size()); o.insert(o.end(), data.begin(), data.end()); }
  return o;
}
inline std::optional<archx::dmx::link::DmxFrame> testParse(const std::uint8_t* p, std::size_t n) {
  using archx::dmx::link::DmxFrame;
  DmxFrame f;
  std::size_t i = 0;
  auto varint = [&](std::uint64_t& v) {
    v = 0;
    for (int s = 0; s < 70; s += 7) {
      if (i >= n) return false;
      std::uint8_t b = p[i++];
      v |= static_cast<std::uint64_t>(b & 0x7F) << s;
      if (!(b & 0x80)) return true;
    }
    return false;
  };
  while (i < n) {
    std::uint64_t tag, v;
    if (!varint(tag)) return std::nullopt;
    if (tag == 0x08) {
      if (!varint(v)) return std::nullopt;
      f.universe = static_cast<std::uint32_t>(v);
    } else if (tag == 0x12) {
      if (!varint(v) || v > n - i) return std::nullopt;
      f.data.assign(p + i, p + i + v);
      i += v;
    } else {
      return std::nullopt;  // streng: unbekanntes Feld = kaputt (nur für Tests)
    }
  }
  return f;
}
inline Bytes framed(const Bytes& payload) {
  Bytes o;
  for (int k = 0; k < 4; ++k) o.push_back(static_cast<std::uint8_t>((payload.size() >> (8 * k)) & 0xFF));
  o.insert(o.end(), payload.begin(), payload.end());
  return o;
}
// Kompletter Draht-Rahmen für ein DmxFrame mit data = 512 x fill.
inline Bytes wireMessage(std::uint32_t universe, std::uint8_t fill, std::size_t dataSize = 512) {
  return framed(wireFrame(universe, Bytes(dataSize, fill)));
}
inline Bytes concat(std::initializer_list<Bytes> parts) {
  Bytes o;
  for (auto& p : parts) o.insert(o.end(), p.begin(), p.end());
  return o;
}

// ---------------------------------------------------------------- Sink
class FakeSink : public archx::dmx::link::FrameSink {
 public:
  struct Entry {
    std::uint16_t universe;
    archx::dmx::link::UniverseData data;
  };
  void setFrame(std::uint16_t u, const archx::dmx::link::UniverseData& d) override {
    std::lock_guard<std::mutex> l(m_);
    entries_.push_back({u, d});
  }
  std::size_t count() const {
    std::lock_guard<std::mutex> l(m_);
    return entries_.size();
  }
  std::vector<Entry> all() const {
    std::lock_guard<std::mutex> l(m_);
    return entries_;
  }
  // Letzter Rahmen eines Universums (oder nullopt).
  std::optional<Entry> last(std::uint16_t u) const {
    std::lock_guard<std::mutex> l(m_);
    for (auto it = entries_.rbegin(); it != entries_.rend(); ++it)
      if (it->universe == u) return *it;
    return std::nullopt;
  }
 private:
  mutable std::mutex m_;
  std::vector<Entry> entries_;
};
inline bool allBytesEqual(const archx::dmx::link::UniverseData& d, std::uint8_t v) {
  return std::all_of(d.begin(), d.end(), [v](std::uint8_t b) { return b == v; });
}

// ---------------------------------------------------------------- TCP-Client für Tests
class TcpClient {
 public:
  ~TcpClient() { close(); }
  bool connect(std::uint16_t port) {
    close();
    fd_ = ::socket(AF_INET, SOCK_STREAM, 0);
    sockaddr_in a{};
    a.sin_family = AF_INET;
    a.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    a.sin_port = htons(port);
    if (fd_ < 0 || ::connect(fd_, reinterpret_cast<sockaddr*>(&a), sizeof a) != 0) { close(); return false; }
    return true;
  }
  bool sendAll(const Bytes& b) {
    std::size_t off = 0;
    while (off < b.size()) {
      ssize_t n = ::send(fd_, b.data() + off, b.size() - off, MSG_NOSIGNAL);
      if (n <= 0) return false;
      off += static_cast<std::size_t>(n);
    }
    return true;
  }
  bool sendBytewise(const Bytes& b, int delayUs = 50) {
    for (auto byte : b) {
      if (::send(fd_, &byte, 1, MSG_NOSIGNAL) != 1) return false;
      std::this_thread::sleep_for(std::chrono::microseconds(delayUs));
    }
    return true;
  }
  // true, sobald die Gegenseite die Verbindung geschlossen hat (EOF oder Reset).
  bool waitForClose(int timeoutMs = 2000) {
    auto end = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    while (std::chrono::steady_clock::now() < end) {
      pollfd p{fd_, POLLIN, 0};
      if (::poll(&p, 1, 20) > 0) {
        char c[64];
        ssize_t n = ::recv(fd_, c, sizeof c, 0);
        if (n <= 0) return true;
      }
    }
    return false;
  }
  bool isOpen() const { return fd_ >= 0; }
  void close() {
    if (fd_ >= 0) ::close(fd_);
    fd_ = -1;
  }
 private:
  int fd_ = -1;
};

}  // namespace archx::test
