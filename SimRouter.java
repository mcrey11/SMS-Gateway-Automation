import android.content.Context;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.List;

/**
 * Routes transactions to appropriate SIM card based on network
 * Handles dual-SIM phone configuration
 */
public class SimRouter {
    private static final String TAG = "SimRouter";
    
    // Network carrier identifiers
    private static final String SMART_MCC_MNC = "51502"; // Smart Communications
    private static final String GLOBE_MCC_MNC = "51502"; // Globe Telecom (different MNC)
    
    /**
     * Get SIM slot for specific network
     * 
     * @param context Application context
     * @param network Network name ("SMART" or "GLOBE")
     * @return SIM slot index (0 or 1), or -1 if not found
     */
    public static int getSimSlotForNetwork(Context context, String network) {
        if (context == null || network == null) {
            Log.e(TAG, "Invalid context or network");
            return -1;
        }
        
        Log.i(TAG, "Finding SIM for network: " + network);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return getSimSlotApi22(context, network);
        } else {
            return getSimSlotLegacy(context, network);
        }
    }
    
    /**
     * Get SIM slot using SubscriptionManager API (Android 5.1+)
     */
    private static int getSimSlotApi22(Context context, String network) {
        SubscriptionManager subscriptionManager = (SubscriptionManager) 
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        
        if (subscriptionManager == null) {
            Log.e(TAG, "SubscriptionManager not available");
            return -1;
        }
        
        try {
            List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
            
            if (subscriptions == null || subscriptions.isEmpty()) {
                Log.e(TAG, "No active SIM subscriptions found");
                return -1;
            }
            
            Log.i(TAG, "Found " + subscriptions.size() + " active SIM(s)");
            
            for (SubscriptionInfo info : subscriptions) {
                String carrierName = info.getCarrierName().toString().toUpperCase();
                String displayName = info.getDisplayName().toString().toUpperCase();
                int simSlot = info.getSimSlotIndex();
                
                Log.d(TAG, String.format("SIM %d: Carrier=%s, Display=%s, MCC-MNC=%s%s",
                        simSlot, carrierName, displayName, info.getMcc(), info.getMnc()));
                
                // Match by carrier name
                if (network.equalsIgnoreCase("SMART")) {
                    if (carrierName.contains("SMART") || 
                        displayName.contains("SMART") ||
                        carrierName.contains("TNT") ||
                        displayName.contains("TNT")) {
                        Log.i(TAG, "Found SMART SIM in slot " + simSlot);
                        return simSlot;
                    }
                } else if (network.equalsIgnoreCase("GLOBE")) {
                    if (carrierName.contains("GLOBE") || 
                        displayName.contains("GLOBE") ||
                        carrierName.contains("TM") ||
                        displayName.contains("TM")) {
                        Log.i(TAG, "Found GLOBE SIM in slot " + simSlot);
                        return simSlot;
                    }
                }
            }
            
            // If no match found, log available SIMs
            Log.w(TAG, "No SIM found for network: " + network);
            for (SubscriptionInfo info : subscriptions) {
                Log.w(TAG, "  Available: " + info.getCarrierName() + " in slot " + info.getSimSlotIndex());
            }
            
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for reading SIM info", e);
        }
        
        return -1;
    }
    
    /**
     * Get SIM slot using TelephonyManager (Legacy method)
     */
    private static int getSimSlotLegacy(Context context, String network) {
        TelephonyManager telephonyManager = (TelephonyManager) 
                context.getSystemService(Context.TELEPHONY_SERVICE);
        
        if (telephonyManager == null) {
            Log.e(TAG, "TelephonyManager not available");
            return -1;
        }
        
        try {
            String operatorName = telephonyManager.getNetworkOperatorName().toUpperCase();
            Log.d(TAG, "Network operator: " + operatorName);
            
            if (network.equalsIgnoreCase("SMART")) {
                if (operatorName.contains("SMART") || operatorName.contains("TNT")) {
                    return 0; // Assume first SIM
                }
            } else if (network.equalsIgnoreCase("GLOBE")) {
                if (operatorName.contains("GLOBE") || operatorName.contains("TM")) {
                    return 0; // Assume first SIM
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for reading phone state", e);
        }
        
        return -1;
    }
    
    /**
     * Get subscription ID for SIM slot
     * 
     * @param context Application context
     * @param simSlot SIM slot index
     * @return Subscription ID or -1 if not found
     */
    public static int getSubscriptionId(Context context, int simSlot) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return -1;
        }
        
        SubscriptionManager subscriptionManager = (SubscriptionManager) 
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        
        if (subscriptionManager == null) {
            return -1;
        }
        
        try {
            List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
            
            if (subscriptions != null) {
                for (SubscriptionInfo info : subscriptions) {
                    if (info.getSimSlotIndex() == simSlot) {
                        return info.getSubscriptionId();
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied", e);
        }
        
        return -1;
    }
    
    /**
     * Get detailed SIM information
     * 
     * @param context Application context
     * @return String with SIM configuration details
     */
    public static String getSimInfo(Context context) {
        StringBuilder info = new StringBuilder();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subscriptionManager = (SubscriptionManager) 
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            
            if (subscriptionManager != null) {
                try {
                    List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
                    
                    if (subscriptions != null && !subscriptions.isEmpty()) {
                        info.append("Active SIMs: ").append(subscriptions.size()).append("\n");
                        
                        for (SubscriptionInfo sub : subscriptions) {
                            info.append(String.format("Slot %d: %s (%s) - Sub ID: %d\n",
                                    sub.getSimSlotIndex(),
                                    sub.getCarrierName(),
                                    sub.getDisplayName(),
                                    sub.getSubscriptionId()));
                        }
                    } else {
                        info.append("No active SIM cards found\n");
                    }
                } catch (SecurityException e) {
                    info.append("Permission denied to read SIM info\n");
                }
            }
        } else {
            info.append("SIM info requires Android 5.1+\n");
        }
        
        return info.toString();
    }
    
    /**
     * Check if dual SIM is supported
     * 
     * @param context Application context
     * @return true if device has multiple SIM slots
     */
    public static boolean isDualSimSupported(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subscriptionManager = (SubscriptionManager) 
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            
            if (subscriptionManager != null) {
                try {
                    List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
                    return subscriptions != null && subscriptions.size() > 1;
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission denied", e);
                }
            }
        }
        return false;
    }
}
