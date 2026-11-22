#include "dmx_output.h"

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

#include <chrono>
#include <stdexcept>
#include <utility>

namespace archx {

DmxOutput::DmxOutput(DmxOutputConfig config) : config_(std::move(config)) {}

DmxOutput::~DmxOutput() {
    Stop();
}

void DmxOutput::SetFrame(uint16_t universe, const std::vector<uint8_t>& data) {
    // TODO:
    //  1. Angaben prüfen: universe 1 bis 32768, data 1 bis 512 Werte. Sonst std::invalid_argument werfen.
    //  2. Den Mutex sperren (std::lock_guard<std::mutex> lock(mutex_);) und den Rahmen in frames_ ablegen.
    //     Der Mutex wird nur kurz gehalten: nur kopieren, nichts senden.

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
    // TODO:
    //  1. Ist die Schleife schon aktiv (running_), passiert nichts.
    //  2. Einen UDP-Socket anlegen: socket_ = socket(AF_INET, SOCK_DGRAM, 0); bei einem Fehler std::runtime_error werfen.
    //  3. Mit setsockopt(socket_, SOL_SOCKET, SO_BROADCAST, ...) Broadcast erlauben,
    //     sonst kann man nicht an eine Broadcast-Adresse senden.
    //  4. running_ = true; und den Thread starten: thread_ = std::thread(&DmxOutput::Run, this);
    if (running_) {

    }
}

void DmxOutput::Stop() {
    // TODO:
    //  1. running_ = false;
    //  2. Wenn thread_.joinable(): thread_.join(); (wartet, bis der Thread fertig ist)
    //     Ein std::thread, der beim Zerstören noch läuft, beendet das ganze Programm. Deshalb immer joinen.
    //  3. Den Socket schließen (close) und socket_ auf -1 setzen, falls er offen ist.
}

uint64_t DmxOutput::PacketsSent() const {
    return packets_sent_.load();
}

void DmxOutput::Run() {
    // TODO: die Schleife, solange running_ wahr ist.
    //
    //  Vor der Schleife:
    //   - Die Zieladresse als sockaddr_in vorbereiten: sin_family = AF_INET, sin_port = htons(config_.port),
    //     inet_pton(AF_INET, config_.target_ip.c_str(), &addr.sin_addr).
    //   - Die Dauer eines Taktes: std::chrono::duration<double>(1.0 / config_.frames_per_second).
    //     Mit std::chrono::duration_cast in steady_clock::duration umwandeln.
    //   - next_tick = std::chrono::steady_clock::now();
    //   - Eine Folgenummer pro Universum: std::map<uint16_t, uint8_t> sequences;
    //
    //  In jedem Durchlauf:
    //   1. Die Rahmen kopieren: unter dem Mutex "auto frames = frames_;" und den Mutex wieder freigeben.
    //      So wartet SetFrame nie auf das Senden.
    //   2. Für jedes Universum (u, data) in frames:
    //        - Folgenummer bestimmen: von 1 bis 255 hochzählen, nach 255 wieder 1 (die 0 heißt "nicht benutzt").
    //        - Paket bauen: BuildArtDmx(u - 1, sequence, data)
    //        - Mit sendto(...) senden und packets_sent_++.
    //   3. Den nächsten Takt bestimmen: next_tick += period; und mit std::this_thread::sleep_until(next_tick) warten.
    //      Mit sleep_until statt sleep_for sammelt sich kein Fehler an: Ein langsamer Durchlauf
    //      verkürzt nur das nächste Warten.
    //      Hinterherhinken: Ist next_tick schon vorbei (um mehr als einen Takt), setze next_tick = now,
    //      damit der Thread nicht in Windeseile mehrere Takte nachholt.
}

}  // namespace archx
