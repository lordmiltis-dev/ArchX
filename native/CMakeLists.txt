cmake_minimum_required(VERSION 3.20)
project(archx_native CXX)

set(CMAKE_CXX_STANDARD 20)
enable_testing()

add_executable(archx_hello main.cpp)
add_test(NAME smoke COMMAND archx_hello)

