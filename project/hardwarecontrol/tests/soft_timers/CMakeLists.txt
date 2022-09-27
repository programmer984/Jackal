cmake_minimum_required(VERSION 3.22)
project(soft_timers C)

set(CMAKE_C_STANDARD 11)
include_directories(Kernel)

file(GLOB_RECURSE SOURCES "main.c" "Kernel/common.c"  "Kernel/linkedList.c" "Kernel/soft_timer.c" )
add_executable(soft_timers main.c ${SOURCES})
