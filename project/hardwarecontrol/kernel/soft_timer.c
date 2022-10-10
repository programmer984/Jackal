#include "common.h"
#include "soft_timer.h"
#include <stdlib.h>
#include <stdio.h>
#include "linked_list.h"


volatile u32 timeStampCounter = 0;
LinkedList_t softTimers;

u32 getSoftTimestamp() {
    return timeStampCounter;
}

SoftTimer_t *addTimer(u16 period, bool repeatable, void (*funcPtr)(void *), void *state) {
    SoftTimer_t *timer = malloc(sizeof(SoftTimer_t));
    timer->pending = false;
    timer->period = period;
    timer->removed = false;
    timer->nextInvoke = timeStampCounter + period;
    timer->repeatable = repeatable;
    timer->funcPtr = funcPtr;
    timer->state = state;
    addListItem(&softTimers, timer);
    return timer;
}

//don't call in invoking timer function
void removeTimer(SoftTimer_t *timer) {
    if (!timer->removed) {
        timer->removed = true;
        removeListItemByDataPointer(&softTimers, timer);
        free(timer);
    }
}

void restartTimer(SoftTimer_t *timer) {
    timer->nextInvoke = timeStampCounter + timer->period;
}

void hardwareTimerInvoke() {
    timeStampCounter += SOFT_TIMER_MS_PER_TICK;

    if (!softTimers.currentlyChanging) {
        ListIterator_t iterator;
        iterator.list = &softTimers;
        iterator.itemPtr = softTimers.head;

        while (hasNext(&iterator)) {
            SoftTimer_t *timer = (SoftTimer_t *) getAndMove(&iterator);
            if (!timer->pending) {
                if (timeStampCounter >= timer->nextInvoke) {
                    if (timer->repeatable == true) {
                        timer->nextInvoke = timeStampCounter + timer->period;
                    }
                    timer->pending = true;
                }
            }
        }
    }
}


void invokePendingTimers() {
    ListIterator_t iterator;
    iterator.list = &softTimers;
    iterator.itemPtr = softTimers.head;

    while (hasNext(&iterator)) {
        SoftTimer_t *timer = (SoftTimer_t *) getAndMove(&iterator);
        if (timer->pending) {
            bool shouldBeRemoved = timer->funcPtr(timer->state);
            timer->pending = false;
            if (!timer->repeatable || shouldBeRemoved) {
                //remove it
                removeTimer(timer);
            }
        }
    }

}