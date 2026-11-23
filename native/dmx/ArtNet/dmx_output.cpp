// Implements a threaded UDP sender that periodically sends the latest DMX frame per universe.

#include "dmx_output.h"

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <chrono>
#include <stdexcept>
#include <utility>

namespace archx {
    DmxOutput::DmxOutput(DmxOutputConfig config) : config_(std::move(config)) {
    }

    DmxOutput::~DmxOutput() {
        Stop();
    }

    void DmxOutput::SetFrame(uint16_t universe, const std::vector<uint8_t> &data) {
        if (universe < 1 || universe > 32768) {
            throw std::invalid_argument("Invalid universe");
        }

        if (data.size() < 1 || data.size() > 512) {
            throw std::invalid_argument("Invalid data size");
        }

        std::lock_guard<std::mutex> lock(mutex_);
        frames_.insert_or_assign(universe, data);
    }

    void DmxOutput::Start() {
        if (!running_) {
            socket_ = socket(AF_INET, SOCK_DGRAM, 0);
            if (socket_ == -1) {
                throw std::runtime_error("Could not create socket");
            }

            int broadcast = 1;
            if (setsockopt(socket_, SOL_SOCKET, SO_BROADCAST,
                           reinterpret_cast<const char *>(&broadcast), sizeof(broadcast)) == -1) {
                close(socket_);
                socket_ = -1;
                throw std::runtime_error("Could not set socket to broadcast");
            }
            running_ = true;
            thread_ = std::thread(&DmxOutput::Run, this);
        }
    }

    void DmxOutput::Stop() {
        running_ = false;
        if (thread_.joinable()) {
            thread_.join();
        }

        if (socket_ != -1) {
            close(socket_);
            socket_ = -1;
        }
    }

    uint64_t DmxOutput::PacketsSent() const {
        return packets_sent_.load();
    }

    void DmxOutput::Run() {
        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(config_.port);

        if (inet_pton(AF_INET, config_.target_ip.c_str(), &addr.sin_addr) != 1) {
            throw std::runtime_error("Invalid target IP address");
        }

        const auto tick = std::chrono::duration_cast<std::chrono::steady_clock::duration>(
            std::chrono::duration<double>(1.0 / config_.frames_per_second));

        auto next_tick = std::chrono::steady_clock::now();

        std::map<uint16_t, uint8_t> sequences;

        while (running_) {
            std::map<uint16_t, std::vector<uint8_t> > frames;

            {
                std::lock_guard<std::mutex> lock(mutex_);
                frames = frames_;
            }

            for (const auto &[universe, data]: frames) {
                uint8_t &sequence = sequences[universe];

                if (sequence == 0 || sequence == 255) {
                    sequence = 1;
                } else {
                    sequence++;
                }

                const std::vector<uint8_t> packet = BuildArtDmx(universe - 1, sequence, data);

                sendto(socket_, packet.data(), packet.size(), 0, reinterpret_cast<const sockaddr *>(&addr),
                       sizeof(addr));

                ++packets_sent_;
            }

            next_tick += tick;

            auto now = std::chrono::steady_clock::now();

            if (next_tick < now - tick) {
                next_tick = now;
            }

            std::this_thread::sleep_until(next_tick);
        }
    }
} // namespace archx
