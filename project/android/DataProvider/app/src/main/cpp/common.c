#include "common.h"


s16 getS16(u8* buf){
	s16 result = *(buf);
	result |= ((s16)*(buf+1))<<8;
	return result;
}

u16 getU16(u8* buf){
	u16 result = *(buf);
	result |= ((u16)*(buf+1))<<8;
	return result;
}

u32 getU32(u8* buf){
    u16 result = *(buf);
    result |= ((u32)*(buf+1))<<8;
    result |= ((u32)*(buf+2))<<16;
    result |= ((u32)*(buf+3))<<24;
    return result;
}

s64 getS64(u8* buf){
	s64 result = *(buf);
	result |= ((s64)*(buf+1))<<8;
	result |= ((s64)*(buf+2))<<16;
	result |= ((s64)*(buf+3))<<24;
	result |= ((s64)*(buf+4))<<32;
	result |= ((s64)*(buf+5))<<40;
	result |= ((s64)*(buf+6))<<48;
	result |= ((s64)*(buf+7))<<56;
	return result;
}

void copy(void* src, void* dst, u16 count){
	int j=0;
	for (j = 0; j < count; j++) {
		*(char *) (dst +  j) =
				*(char *) (src + j);
	}
}
