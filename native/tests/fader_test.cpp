#include <fstream>
#include <iostream>
#include <string>

#include "fader.pb.h"  // generated from schema/fader.proto by protoc

int main() {
    // Path to the file written by the Kotlin program.
    const std::string path = std::string(TESTDATA_DIR) + "/fader_moved.bin";

    std::ifstream in(path, std::ios::binary);
    if (!in) {
        std::cerr << "File not found: " << path << "\n";
        return 1;
    }

    // Convert the bytes into a FaderMoved object.
    archx::FaderMoved msg;
    if (!msg.ParseFromIstream(&in)) {
        std::cerr << "The bytes could not be read\n";
        return 1;
    }

    std::cout << "fader_id = " << msg.fader_id() << "\n"
              << "value    = " << msg.value() << "\n"
              << "touched  = " << (msg.touched() ? "true" : "false") << "\n";

    // Check whether the same values are read as those written by Kotlin.
    const bool ok = msg.fader_id() == 3 && msg.value() == 0.75f && msg.touched();
    if (!ok) {
        std::cerr << "ERROR: the values do not match\n";
        return 1;
    }

    std::cout << "OK: C++ reads the same values that Kotlin wrote\n";
    return 0;
}
