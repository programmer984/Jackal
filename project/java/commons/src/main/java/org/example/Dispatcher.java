package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class Dispatcher implements Runnable, AutoCloseable {
    private final static Logger logger = LoggerFactory.getLogger(Dispatcher.class);
    private final int queueSize;
    private final BlockingQueue<Runnable> tasksQueue;
    private final Thread worker;
    private final Object lock = new Object();

    public Dispatcher(int queueSize, String threadName) {
        this.queueSize = queueSize;
        tasksQueue = new LinkedBlockingDeque<>(queueSize);
        worker = new Thread(this, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    public boolean isFull() {
        return tasksQueue.size() >= queueSize;
    }

    public boolean isEmpty() {
        return tasksQueue.isEmpty();
    }

    public void submitBlocking(Runnable task) {
        try {
            tasksQueue.put(task);
        } catch (InterruptedException e) {
            logger.error("faild to add task", e);
        }
    }

    public void join() throws InterruptedException {
        synchronized (lock) {
            while (!tasksQueue.isEmpty()) {
                lock.wait();
            }
        }
    }

    @Override
    public void close() throws Exception {
        worker.interrupt();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Runnable nextTask = tasksQueue.poll(20, TimeUnit.MILLISECONDS);
                if (nextTask != null) {
                    nextTask.run();
                } else {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                logger.error("Dispatcher", e);
            }
        }
    }


}
