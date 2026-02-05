package com.gateway.sms;

import java.util.UUID;

/**
 * Transaction data model for reload requests
 */
public class Transaction {
    public String reference;      // Unique transaction ID
    public String msisdn;          // Mobile number (09XXXXXXXXX)
    public String promo;           // Promo code (GIGA99, GOSURF50, etc.)
    public int amount;             // Amount in pesos
    public String network;         // SMART or GLOBE
    public String sender;          // SMS sender (optional)
    public String status;          // QUEUED, PROCESSING, SUCCESS, FAILED
    public long timestamp;         // Creation timestamp
    public String errorMessage;    // Error details if failed
    public int retryCount;         // Number of retry attempts

    public Transaction() {
        this.reference = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.status = "QUEUED";
        this.timestamp = System.currentTimeMillis();
        this.retryCount = 0;
    }

    @Override
    public String toString() {
        return String.format("Transaction[%s: %s %s %d PHP via %s - %s]",
                reference, msisdn, promo, amount, network, status);
    }
}
