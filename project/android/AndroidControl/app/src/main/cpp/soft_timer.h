#include "common.h"
#include "stdbool.h"

#ifndef SOFT_TIMERS_H_
#define SOFT_TIMERS_H_

#define SOFT_TIMER_MS_PER_TICK 1


typedef struct {
    u16 period;
    u32 nextInvoke;
    bool pending; //awaiting main loop to invoke it
    bool repeatable; //after pending state on, increase nextInvoke time to invoke
    bool removed;
    bool (*funcPtr)(void *state); //true - if repeatable timer should be removed
    void *state;
} SoftTimer_t;

extern SoftTimer_t* addTimer(u16 period, bool repeatable, bool (*funcPtr)(void*), void* state);
extern void Error_Handler();

//don't call in invoking timer function
extern void removeTimer(SoftTimer_t* timer);

extern void restartTimer(SoftTimer_t* timer);
extern void invokePendingTimers();
extern void hardwareTimerInvoke();
extern u32 getSoftTimestamp();

#endif
