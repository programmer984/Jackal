#include <stdio.h>
#include <stdbool.h>
#include "common.h"
#include "soft_timer.h"
#include <stdlib.h>


int count = 0;

void log(char message[]) {
    printf(message);
    printf("\n");
}

void beforeTest() {
    count = 0;
}


void timerCallback(void* param){
    count++;
}

void invoke(int times){
    for (int i=0;i< times;i++) {
        hardwareTimerInvoke();
    }
}

void assertEquals(int a, int b, int exitCode) {
    if (a != b) {
        exit(exitCode);
    }
}


LinkedList_t list;
LinkedList_t* listPtr = &list;
int main() {
    printf("Hello, World! LinkedList test\n");
    log("Add items to list");
    ListItem_t* firstItem = addListItem(listPtr, NULL);
    ListItem_t* middleItem = addListItem(listPtr, NULL);
    ListItem_t* lastItem = addListItem(listPtr, NULL);
    removeListItem(listPtr, lastItem);
    assertEquals(list.count, 2, 101);
    assertEquals(list.tail, middleItem, 102);

    removeListItem(listPtr, firstItem);
    assertEquals(list.head, middleItem, 103);


    clearList(listPtr);
    firstItem = addListItem(listPtr, NULL);
    middleItem = addListItem(listPtr, NULL);
    lastItem = addListItem(listPtr, NULL);
    removeListItem(listPtr, middleItem);
    assertEquals(firstItem->prev, NULL, 104);
    assertEquals(lastItem->next, NULL, 105);

    printf("Soft timer test\n");
    beforeTest();
    log("One invoke");
    softTimer_t* timer = addTimer(100, false, &timerCallback, NULL);
    invoke(100);
    invokePendingTimers();
    assertEquals(count, 1, 1);

    invoke(100);
    invokePendingTimers();
    assertEquals(count, 1, 2);


    beforeTest();
    log("no pending timers");
    timer = addTimer(100, false, &timerCallback, NULL);
    invokePendingTimers();
    assertEquals(count, 0, 3);

    beforeTest();
    log("2 timers without reference with equal periods");
    addTimer(10, false, &timerCallback, NULL);
    addTimer(10, false, &timerCallback, NULL);
    invoke(10);
    invokePendingTimers();
    assertEquals(count, 2, 4);



    return 0;
}
