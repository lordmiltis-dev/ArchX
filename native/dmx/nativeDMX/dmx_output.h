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
    std::string target_ip = "127.0.0.1";   // Ziel der Pakete: eine Adresse (Unicast) oder eine Broadcast-Adresse
    uint16_t port = kArtNetPort;
    double frames_per_second = 44.0;
};

// Sendet die zuletzt gesetzten Rahmen jedes Universums als Art-Net, in einer festen Schleife.
// Wird ein Rahmen nicht geändert, wird er trotzdem immer wieder gesendet, denn Geräte erwarten einen
// regelmäßigen Datenstrom.
//
// SetFrame darf aus jedem Thread aufgerufen werden und wartet nie auf das Netzwerk.
class DmxOutput {
public:
    explicit DmxOutput(DmxOutputConfig config = {});
    ~DmxOutput();   // beendet die Schleife, falls sie noch läuft

    DmxOutput(const DmxOutput&) = delete;
    DmxOutput& operator=(const DmxOutput&) = delete;

    // Merkt sich den neuesten Rahmen eines Universums.
    // universe zählt ab 1 (bis 32768) und wird als Art-Net-Universum universe-1 gesendet.
    // data hat 1 bis 512 Werte. Wirft std::invalid_argument bei ungültigen Angaben.
    void SetFrame(uint16_t universe, const std::vector<uint8_t>& data);

    // Startet den Sendethread. Wirft std::runtime_error, wenn der Socket nicht angelegt werden kann.
    void Start();

    // Beendet die Schleife und wartet, bis der Thread fertig ist. Mehrfaches Aufrufen ist harmlos.
    void Stop();

    // Anzahl der bisher gesendeten Pakete (alle Universen zusammen).
    uint64_t PacketsSent() const;

private:
    void Run();   // die Schleife des Sendethreads

    DmxOutputConfig config_;
    std::mutex mutex_;                                   // schützt frames_
    std::map<uint16_t, std::vector<uint8_t>> frames_;    // Universum (ab 1) -> letzter Rahmen
    std::atomic<bool> running_{false};
    std::atomic<uint64_t> packets_sent_{0};
    std::thread thread_;
    int socket_ = -1;
};

}  // namespace archx
