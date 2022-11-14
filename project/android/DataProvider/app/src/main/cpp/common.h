//for types reference
#include <stdint.h>
#include "stdbool.h"
#include "stddef.h"
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#ifndef __COMMON_H
#define __COMMON_H

typedef unsigned int u32;
typedef long long s64;
typedef unsigned short int u16;
typedef short int s16;
typedef unsigned char u8;


extern s16 getS16(u8* buf);
extern u16 getU16(u8* buf);
extern u32 getU32(u8* buf);
extern s64 getS32(u8* buf);
extern s64 getS64(u8* buf);
extern void copy(void* src, void* dst, u16 count);
#endif
