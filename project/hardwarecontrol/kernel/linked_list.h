#include "common.h"


#ifndef LINKEDLIST_H_
#define LINKEDLIST_H_


typedef struct
{    
    void* data;
    struct ListItem_t* prev;
    struct ListItem_t* next;
} ListItem_t;


typedef struct
{
    bool currentlyChanging:1;
    u16 count:15;
    ListItem_t* head;
    ListItem_t* tail;
} LinkedList_t;

typedef struct
{
    LinkedList_t* list;
    ListItem_t* itemPtr;
}ListIterator_t;


extern ListItem_t *addListItem(LinkedList_t *list, void *data);
//remember - you MUST free result after using
extern void removeListItemByDataPointer(LinkedList_t* list, void* dataPtr);
extern void clearListAndFree(LinkedList_t *list);
extern bool isListEmpty(LinkedList_t* list);

extern bool hasNext(ListIterator_t* iterator);
extern void* getAndMove(ListIterator_t* iterator);

#endif /* LINKEDLIST_H_ */
