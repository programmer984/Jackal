package org.example.rollingHeap;

import java.util.Map;
import java.util.WeakHashMap;

public class RollingHeap<T> {
    private final ItemProducer<T> producer;
    private Map<T, Boolean> usingMap = new WeakHashMap<>();

    public RollingHeap(ItemProducer<T> producer) {
        this.producer = producer;
    }

    public synchronized T getItemForWriting() {
        for (Map.Entry<T, Boolean> item : usingMap.entrySet()) {
            if (item.getValue() == false) {
                usingMap.put(item.getKey(), true);
                return item.getKey();
            }
        }

        T newItem = producer.createNew();
        usingMap.put(newItem, true);
        return newItem;
    }

    public synchronized void freeItemAfterUsing(T item) {
        usingMap.put(item, false);
    }
}
