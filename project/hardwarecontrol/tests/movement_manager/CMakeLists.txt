cmake_minimum_required(VERSION 3.22)
project(movement_manager)

set(CMAKE_C_STANDARD 11)
include_directories(Kernel)

file(GLOB_RECURSE SOURCES "main.c" "Kernel/common.c" "Kernel/movement.c" "Kernel/soft_timer.c"  "Kernel/linked_list.c")

add_executable(movement_manager_test main.c ${SOURCES})
