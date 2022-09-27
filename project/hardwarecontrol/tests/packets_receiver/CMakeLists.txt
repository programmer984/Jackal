cmake_minimum_required(VERSION 3.22)
project(packets_receiver)

set(CMAKE_C_STANDARD 11)
include_directories(Kernel)

file(GLOB_RECURSE SOURCES "main.c" "Kernel/common.c"  "Kernel/search.c" "Kernel/packets_receiver.c")

add_executable(packets_receiver_test main.c ${SOURCES})
