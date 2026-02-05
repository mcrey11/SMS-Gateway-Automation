import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

/**
 * Broadcast receiver for incoming SMS messages
 * Filters reload request messages and queues them for processing
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            Log.e(TAG, "Null context or intent");
            return;
        }

        try {
            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                Log.w(TAG, "No extras in intent");
                return;
            }

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null || pdus.length == 0) {
                Log.w(TAG, "No PDUs in bundle");
                return;
            }

            String format = bundle.getString("format");
            
            for (Object pdu : pdus) {
                if (pdu == null) continue;

                try {
                    SmsMessage msg;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        msg = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        msg = SmsMessage.createFromPdu((byte[]) pdu);
                    }

                    if (msg == null) {
                        Log.w(TAG, "Failed to create SmsMessage from PDU");
                        continue;
                    }

                    String sender = msg.getOriginatingAddress();
                    String body = msg.getMessageBody();

                    if (body == null || body.trim().isEmpty()) {
                        Log.w(TAG, "Empty SMS body");
                        continue;
                    }

                    Log.i(TAG, "Received SMS from " + sender + ": " + body);

                    // Parse the SMS
                    Transaction t = ReloadParser.parse(body);
                    
                    if (t != null) {
                        // Store sender information
                        t.sender = sender;
                        
                        // Enqueue transaction
                        boolean success = GatewayService.enqueue(context, t);
                        
                        if (success) {
                            Log.i(TAG, "Transaction queued successfully");
                            Toast.makeText(context, 
                                "Reload queued: " + t.promo + " - " + t.amount,
                                Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "Failed to enqueue transaction");
                            Toast.makeText(context,
                                "Failed to queue reload",
                                Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "Invalid reload format: " + body);
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error processing PDU", e);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onReceive", e);
        }
    }
}
