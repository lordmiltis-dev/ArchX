cmake_minimum_required(VERSION 3.20)
project(archx_native CXX)

set(CMAKE_CXX_STANDARD 20)
enable_testing()

add_executable(archx_hello tests/main.cpp)
add_test(NAME smoke COMMAND archx_hello)

find_package(Protobuf REQUIRED)

protobuf_generate_cpp(PROTO_SRCS PROTO_HDRS
        ${CMAKE_CURRENT_SOURCE_DIR}/../schema/fader.proto)

add_executable(fader_test tests/fader_test.cpp ${PROTO_SRCS} ${PROTO_HDRS})
target_include_directories(fader_test PRIVATE ${CMAKE_CURRENT_BINARY_DIR})
target_link_libraries(fader_test PRIVATE protobuf::libprotobuf)
target_compile_definitions(fader_test PRIVATE
        TESTDATA_DIR="${CMAKE_CURRENT_SOURCE_DIR}/../schema/testdata")
add_test(NAME fader_roundtrip COMMAND fader_test)
