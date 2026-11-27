
// Sends a sample DMX frame over Art-Net for testing a configured output target.

#include <chrono>
#include <iostream>
#include <thread>
#include <vector>

#include "ArtNet/dmx_output.h"

int main() {
    archx::DmxOutputConfig config;
    config.target_ip = "192.168.178.62";
    config.port = archx::kArtNetPort;
    config.frames_per_second = 40.0;

    archx::DmxOutput output(config);

    std::vector<uint8_t> frame(512, 0);
    frame[0] = 255; // Set DMX channel 1 to 255.

    output.SetFrame(1, frame);
    output.Start();

    std::cout << "Sending Universe 1, Channel 1 = 255 at 40 FPS..."
              << std::endl;

    std::this_thread::sleep_for(std::chrono::seconds(3));

    std::cout << "Packets sent: " << output.PacketsSent() << std::endl;

    output.Stop();
    return 0;
}