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
    result |= ((u16)*(buf+1))<<8;
    result |= ((u16)*(buf+2))<<16;
    result |= ((u16)*(buf+3))<<24;
    return result;
}

void copy(void* src, void* dst, u16 count){
	int j=0;
	for (j = 0; j < count; j++) {
		*(char *) (dst +  j) =
				*(char *) (src + j);
	}
}
