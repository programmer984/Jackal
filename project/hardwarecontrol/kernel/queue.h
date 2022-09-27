#include "common.h"


#ifndef QUEUE_H_
#define QUEUE_H_


typedef struct
{    
    void* data;
    u16	dataSize;
    struct QueueItem_t* next;
} QueueItem_t;


typedef struct
{
    u16 count:16;
    u16 maxCount:16;    
    QueueItem_t* head;
    QueueItem_t* tail;
} KernelQueue_t;


extern void enqueue(KernelQueue_t* queue, void* data, u16 dataSize);

//remember - you MUST mfree result after using
extern QueueItem_t* dequeue(KernelQueue_t* queue);

extern bool isEmpty(KernelQueue_t* queue);
extern bool isFull(KernelQueue_t *queue);

extern void clear(KernelQueue_t *queue);

#endif /* QUEUE_H_ */
