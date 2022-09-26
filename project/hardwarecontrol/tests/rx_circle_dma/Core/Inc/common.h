//for types reference
#include <stdint.h>
#include "stdbool.h"
#include "stddef.h"
#ifndef __COMMON_H
#define __COMMON_H

#define u32 uint32_t
#define s32 int32_t
#define u16 uint16_t
#define s16 int16_t
#define u8 uint8_t
#define s8 int8_t

extern s16 getS16(u8* buf);
extern u16 getU16(u8* buf);
extern void copy(void* src, void* dst, u16 count);
#endif
