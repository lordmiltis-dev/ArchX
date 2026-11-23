cmake_minimum_required(VERSION 3.20)
project(archx_native CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

enable_testing()

find_package(Protobuf REQUIRED)
find_package(nlohmann_json 3.2.0 REQUIRED)

# --- Schema-Code (aus schema/*.proto), einmal gebaut und von allen Modulen genutzt ---
protobuf_generate_cpp(PROTO_SRCS PROTO_HDRS
    ${CMAKE_CURRENT_SOURCE_DIR}/../schema/fader.proto
    ${CMAKE_CURRENT_SOURCE_DIR}/../schema/showfile.proto)

add_library(archx_schema STATIC ${PROTO_SRCS} ${PROTO_HDRS})
target_include_directories(archx_schema PUBLIC ${CMAKE_CURRENT_BINARY_DIR})
target_link_libraries(archx_schema PUBLIC protobuf::libprotobuf)

# --- Bisherige Tests ---
add_executable(archx_hello tests/main.cpp)
add_test(NAME smoke COMMAND archx_hello)

add_executable(fader_test tests/fader_test.cpp)
target_link_libraries(fader_test PRIVATE archx_schema)
target_compile_definitions(fader_test PRIVATE
    TESTDATA_DIR="${CMAKE_CURRENT_SOURCE_DIR}/../schema/testdata")
add_test(NAME fader_roundtrip COMMAND fader_test)

# --- Fixtures-Library: Importer für das OFL-Format ---
add_library(archx_fixtures STATIC fixtures/ofl_importer.cpp)
target_include_directories(archx_fixtures PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/fixtures)
target_link_libraries(archx_fixtures PUBLIC archx_schema nlohmann_json::nlohmann_json)

add_executable(ofl_importer_test fixtures/tests/ofl_importer_test.cpp)
target_link_libraries(ofl_importer_test PRIVATE archx_fixtures)
add_test(NAME ofl_importer COMMAND ofl_importer_test)

# --- DMX / Art-Net ---
add_library(archx_dmx STATIC dmx/ArtNet/artnet_packet.cpp dmx/ArtNet/dmx_output.cpp)
target_include_directories(archx_dmx PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/dmx)

add_executable(artnet_packet_test dmx/ArtNet/tests/artnet_packet_test.cpp)
target_link_libraries(artnet_packet_test PRIVATE archx_dmx)
add_test(NAME artnet_packet COMMAND artnet_packet_test)



# Werkzeuge zum Ausprobieren (nur Linux und WSL)
add_executable(artnet_monitor dmx/ArtNet/tools/artnet_monitor.cpp)
target_link_libraries(artnet_monitor PRIVATE archx_dmx)

add_executable(artnet_send dmx/ArtNet/tools/artnet_send.cpp)
target_link_libraries(artnet_send PRIVATE archx_dmx)

add_executable(artnet_out dmx/ArtNet/tools/artnet_out.cpp)
target_link_libraries(artnet_out PRIVATE archx_dmx)

# --- DMX-Ausgabe (Sendeschleife) ---
find_package(Threads REQUIRED)
target_sources(archx_dmx PRIVATE dmx/ArtNet/dmx_output.cpp)
target_link_libraries(archx_dmx PUBLIC Threads::Threads)

add_executable(dmx_output_test dmx/ArtNet/tests/dmx_output_test.cpp)
target_link_libraries(dmx_output_test PRIVATE archx_dmx)
add_test(NAME dmx_output COMMAND dmx_output_test)

