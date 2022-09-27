#include <stdlib.h>
#include "common.h"
#include "queue.h"

void enqueue(KernelQueue_t *queue, void *data, u16 dataSize)
{

    if (queue->count < queue->maxCount)
    {
        QueueItem_t *newItem = (QueueItem_t *)malloc(sizeof(QueueItem_t));
        newItem->data = data;
        newItem->dataSize = dataSize;

        if (queue->head == NULL)
        {
            queue->head = newItem;
        }

        if (queue->tail == NULL)
        {
            queue->tail = newItem;
        }
        else
        {
            queue->tail->next = newItem;
            queue->tail = newItem;
        }
        queue->count++;
    }
}

QueueItem_t *dequeue(KernelQueue_t *queue)
{
    QueueItem_t *head = queue->head;
    queue->count--;
    if (queue->count == 0)
    {
        queue->head = NULL;
        queue->tail = NULL;
    }
    else
    {
        queue->head = head->next;
    }
    return head;
}

bool isEmpty(KernelQueue_t *queue)
{
    if (queue->count == 0)
    {
        return true;
    }
    return false;
}

bool isFull(KernelQueue_t *queue)
{
    if (queue->count == queue->maxCount)
    {
        return true;
    }
    return false;
}

void clear(KernelQueue_t *queue)
{
    while (!isEmpty(queue))
    {
        QueueItem_t *item = dequeue(queue);
        free(item->data);
        free(item);
    }
}