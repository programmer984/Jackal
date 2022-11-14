#include "common.h"

int searchSequense(u8 *src, int srcSize, u8 *target, int targetSize) {
    int found = -1;
    if (targetSize <= srcSize) {
        for (int i = 0; i < srcSize; i++) {
            if (src[i] == target[0] && i + targetSize <= srcSize) {
                found = i;
                for (int j = 1; j < targetSize; j++) {
                    if (src[i + j] != target[j]) {
                        found = -1;
                        break;
                    }
                }
                if (found >= 0) {
                    break;
                }
            }
        }
    }
    return found;
}
