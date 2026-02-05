package com.gateway.sms;

import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

/**
 * Thread-safe transaction queue with capacity limits
 * Manages FIFO processing of reload transactions
 */
public class TransactionQueue {
    private static final String TAG = "TransactionQueue";
    private static final int DEFAULT_CAPACITY = 100;

    private final Queue<Transaction> queue = new LinkedList<>();
    private final int maxCapacity;
    private int processedCount = 0;
    private int failedCount = 0;

    public TransactionQueue() {
        this(DEFAULT_CAPACITY);
    }

    public TransactionQueue(int capacity) {
        this.maxCapacity = capacity;
        Log.i(TAG, "TransactionQueue initialized with capacity: " + capacity);
    }

    /**
     * Add transaction to queue
     * @param t Transaction to add
     * @return true if added successfully, false if queue is full
     */
    public synchronized boolean add(Transaction t) {
        if (t == null) {
            Log.w(TAG, "Attempted to add null transaction");
            return false;
        }

        if (queue.size() >= maxCapacity) {
            Log.w(TAG, "Queue is full (" + maxCapacity + "). Cannot add transaction.");
            return false;
        }

        boolean result = queue.add(t);
        if (result) {
            Log.i(TAG, String.format("Transaction added. Queue size: %d/%d",
                    queue.size(), maxCapacity));
        }
        return result;
    }

    /**
     * Get and remove next transaction from queue
     * @return Next transaction or null if queue is empty
     */
    public synchronized Transaction next() {
        Transaction t = queue.poll();
        if (t != null) {
            Log.i(TAG, "Transaction dequeued. Remaining: " + queue.size());
        }
        return t;
    }

    /**
     * Peek at next transaction without removing it
     * @return Next transaction or null if queue is empty
     */
    public synchronized Transaction peek() {
        return queue.peek();
    }

    /**
     * Get current queue size
     * @return Number of transactions in queue
     */
    public synchronized int size() {
        return queue.size();
    }

    /**
     * Check if queue is empty
     * @return true if queue has no transactions
     */
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Check if queue is full
     * @return true if queue has reached maximum capacity
     */
    public synchronized boolean isFull() {
        return queue.size() >= maxCapacity;
    }

    /**
     * Clear all transactions from queue
     */
    public synchronized void clear() {
        int size = queue.size();
        queue.clear();
        Log.w(TAG, "Queue cleared. Removed " + size + " transactions.");
    }

    /**
     * Get a copy of all transactions in queue
     * @return List of transactions
     */
    public synchronized List<Transaction> getAll() {
        return new ArrayList<>(queue);
    }

    /**
     * Record successful transaction processing
     */
    public synchronized void recordSuccess() {
        processedCount++;
        Log.i(TAG, "Success recorded. Total processed: " + processedCount);
    }

    /**
     * Record failed transaction processing
     */
    public synchronized void recordFailure() {
        failedCount++;
        Log.w(TAG, "Failure recorded. Total failed: " + failedCount);
    }

    /**
     * Get statistics
     * @return Statistics string
     */
    public synchronized String getStats() {
        return String.format("Queue: %d/%d, Processed: %d, Failed: %d",
                queue.size(), maxCapacity, processedCount, failedCount);
    }

    /**
     * Get processed transaction count
     * @return Number of successfully processed transactions
     */
    public synchronized int getProcessedCount() {
        return processedCount;
    }

    /**
     * Get failed transaction count
     * @return Number of failed transactions
     */
    public synchronized int getFailedCount() {
        return failedCount;
    }
}
