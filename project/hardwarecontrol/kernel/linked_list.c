#include <stdlib.h>
#include "common.h"
#include "linked_list.h"

ListItem_t *addListItem(LinkedList_t *list, void *data) {

    ListItem_t *newItem = (ListItem_t *) malloc(sizeof(ListItem_t));
    newItem->data = data;
    newItem->next = NULL;

    if (list->head == NULL && list->tail == NULL) {
        list->head = newItem;
        list->tail = newItem;
        newItem->prev = NULL;
    } else {
        newItem->prev = list->tail;
        list->tail->next = newItem;
        list->tail = newItem;
    }

    list->count++;
    return newItem;
}


bool isListEmpty(LinkedList_t *list) {
    if (list->count == 0) {
        return true;
    }
    return false;
}

bool hasNext(ListIterator_t *iterator) {
    return (iterator->itemPtr != NULL);
}

void *getAndMove(ListIterator_t *iterator) {
    void *result = iterator->itemPtr->data;
    iterator->itemPtr = iterator->itemPtr->next;
    return result;
}

void removeListItem(LinkedList_t *list, ListItem_t *listItem) {
    list->currentlyChanging = true;

    ListItem_t *prev = listItem->prev;
    ListItem_t *next = listItem->next;
    //remove from middle
    if (next != NULL && prev != NULL) {
        prev->next = next;
        next->prev = prev;
    }
        //remove only one element (count == 1)
    else if (next == NULL && prev == NULL) {
        list->head = NULL;
        list->tail = NULL;
    }
        //remove from start
    else if (prev == NULL) {
        next->prev = NULL;
        list->head = next;
    }
        //remove from end
    else if (next == NULL) {
        prev->next = NULL;
        list->tail = prev;
    }
    list->count--;
    free(listItem);
    list->currentlyChanging = false;
}


void clearListAndFree(LinkedList_t *list) {
    while (list->count > 0) {
        free(list->head->data);
        removeListItem(list, list->head);
    }
}

ListIterator_t createIterator(LinkedList_t *list){
    ListIterator_t iterator;
    iterator.list = list;
    iterator.itemPtr = list->head;
    return iterator;
}

void removeListItemByDataPointer(LinkedList_t *list, void *dataPtr) {
    ListIterator_t iterator = createIterator(list);
    while (hasNext(&iterator)) {
        ListItem_t * itemPtr = iterator.itemPtr;
        void *itemData = getAndMove(&iterator);
        if (dataPtr == itemData) {
            removeListItem(list, itemPtr);
            break;
        }
    }
}