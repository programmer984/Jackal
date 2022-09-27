cmake_minimum_required(VERSION 3.22)
project(rolling_buf C)

set(CMAKE_C_STANDARD 11)
include_directories(Kernel)

file(GLOB_RECURSE SOURCES "main.c" "Kernel/common.c"  "Kernel/search.c" "Kernel/dma_rolling_buf.c")

add_executable(rolling_buf main.c ${SOURCES})
