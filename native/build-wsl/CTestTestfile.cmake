# CMake generated Testfile for 
# Source directory: /mnt/e/GitHub/ArchX/native
# Build directory: /mnt/e/GitHub/ArchX/native/build-wsl
# 
# This file includes the relevant testing commands required for 
# testing this directory and lists subdirectories to be tested as well.
add_test([=[smoke]=] "/mnt/e/GitHub/ArchX/native/build-wsl/archx_hello")
set_tests_properties([=[smoke]=] PROPERTIES  _BACKTRACE_TRIPLES "/mnt/e/GitHub/ArchX/native/CMakeLists.txt;23;add_test;/mnt/e/GitHub/ArchX/native/CMakeLists.txt;0;")
add_test([=[fader_roundtrip]=] "/mnt/e/GitHub/ArchX/native/build-wsl/fader_test")
set_tests_properties([=[fader_roundtrip]=] PROPERTIES  _BACKTRACE_TRIPLES "/mnt/e/GitHub/ArchX/native/CMakeLists.txt;29;add_test;/mnt/e/GitHub/ArchX/native/CMakeLists.txt;0;")
add_test([=[ofl_importer]=] "/mnt/e/GitHub/ArchX/native/build-wsl/ofl_importer_test")
set_tests_properties([=[ofl_importer]=] PROPERTIES  _BACKTRACE_TRIPLES "/mnt/e/GitHub/ArchX/native/CMakeLists.txt;38;add_test;/mnt/e/GitHub/ArchX/native/CMakeLists.txt;0;")
add_test([=[artnet_packet]=] "/mnt/e/GitHub/ArchX/native/build-wsl/artnet_packet_test")
set_tests_properties([=[artnet_packet]=] PROPERTIES  _BACKTRACE_TRIPLES "/mnt/e/GitHub/ArchX/native/CMakeLists.txt;46;add_test;/mnt/e/GitHub/ArchX/native/CMakeLists.txt;0;")
