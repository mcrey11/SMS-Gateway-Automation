package com.gateway.sms;

import android.util.Log;
import java.util.regex.Pattern;

public class ReloadParser {
    private static final String TAG = "ReloadParser";
    private static final Pattern PHONE_PATTERN = Pattern.compile("^09\\d{9}$");
    private static final int MAX_AMOUNT = 10000;

    /**
     * Parse SMS message into Transaction object
     * Expected format: "09171234567 GIGA99 99"
     * 
     * @param sms SMS message body
     * @return Transaction object or null if invalid
     */
    public static Transaction parse(String sms) {
        if (sms == null || sms.trim().isEmpty()) {
            Log.w(TAG, "Empty SMS message");
            return null;
        }

        String[] parts = sms.trim().split("\\s+");
        if (parts.length != 3) {
            Log.w(TAG, "Invalid SMS format. Expected 3 parts, got " + parts.length);
            return null;
        }

        String msisdn = parts[0];
        String promo = parts[1];
        String amountStr = parts[2];

        // Validate phone number
        if (!PHONE_PATTERN.matcher(msisdn).matches()) {
            Log.w(TAG, "Invalid phone number format: " + msisdn);
            return null;
        }

        // Validate and parse amount
        int amount;
        try {
            amount = Integer.parseInt(amountStr);
            if (amount <= 0 || amount > MAX_AMOUNT) {
                Log.w(TAG, "Amount out of range: " + amount);
                return null;
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid amount format: " + amountStr);
            return null;
        }

        // Validate promo code
        if (promo.length() > 20 || !promo.matches("[a-zA-Z0-9]+")) {
            Log.w(TAG, "Invalid promo code: " + promo);
            return null;
        }

        // Create transaction
        Transaction t = new Transaction();
        t.msisdn = msisdn;
        t.promo = promo.toUpperCase();
        t.amount = amount;

        // Determine network from promo code
        // SMART promos typically start with 'G' (GIGA, GIGASURF, etc.)
        // GLOBE promos typically start with other letters
        if (t.promo.startsWith("G") || t.promo.startsWith("GIGA")) {
            t.network = "SMART";
        } else if (t.promo.startsWith("GO") || t.promo.startsWith("ALL")) {
            t.network = "GLOBE";
        } else {
            // Default or require explicit network detection
            t.network = "UNKNOWN";
            Log.w(TAG, "Could not determine network for promo: " + promo);
        }

        Log.i(TAG, String.format("Parsed transaction: %s - %s - %d (%s)",
                t.msisdn, t.promo, t.amount, t.network));

        return t;
    }
}
