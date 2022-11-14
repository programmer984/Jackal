package org.example.rollingHeap;

@FunctionalInterface
public interface ItemProducer<T> {
    T createNew();
}
