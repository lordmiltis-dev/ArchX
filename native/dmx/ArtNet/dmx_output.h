// Declares the threaded DMX output interface for sending repeated Art-Net frames.

#pragma once

#include <atomic>
#include <cstdint>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "ArtNet/artnet_packet.h"

namespace archx {
    struct DmxOutputConfig {
        std::string target_ip = "127.0.0.1"; // Target: a unicast or broadcast address.
        uint16_t port = kArtNetPort;
        double frames_per_second = 44.0;
    };

    // Sends the latest frame for each universe as Art-Net in a fixed loop.
    // Frames are repeated even when unchanged because devices expect a regular data stream.
    // SetFrame can be called from any thread and never waits for the network.
    class DmxOutput {
    public:
        explicit DmxOutput(DmxOutputConfig config = {});

        ~DmxOutput(); // Stops the loop if it is still running.

        DmxOutput(const DmxOutput &) = delete;

        DmxOutput &operator=(const DmxOutput &) = delete;

        // Stores the latest frame for a universe.
        // Universe starts at 1 (up to 32768) and is sent as Art-Net universe universe-1.
        // Data has 1 to 512 values. Throws std::invalid_argument for invalid arguments.
        void SetFrame(uint16_t universe, const std::vector<uint8_t> &data);

        // Starts the send thread. Throws std::runtime_error if the socket cannot be created.
        void Start();

        // Stops the loop and waits for the thread. Multiple calls are harmless.
        void Stop();

        // Number of packets sent across all universes.
        uint64_t PacketsSent() const;

    private:
        void Run(); // Send-thread loop.

        DmxOutputConfig config_;
        std::mutex mutex_; // Protects frames.
        std::map<uint16_t, std::vector<uint8_t> > frames_; // Universe (starting at 1) -> latest frame.
        std::atomic<bool> running_{false};
        std::atomic<uint64_t> packets_sent_{0};
        std::thread thread_;
        int socket_ = -1;
    };
} // namespace archx
