import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;

/**
 * Foreground service that manages transaction queue and USSD execution
 * Keeps the gateway running continuously
 */
public class GatewayService extends Service {
    private static final String TAG = "GatewayService";
    private static final String CHANNEL_ID = "gateway_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long PROCESSING_INTERVAL = 60000; // 60 seconds between transactions
    
    private static TransactionQueue transactionQueue;
    private HandlerThread workerThread;
    private Handler workerHandler;
    private Handler mainHandler;
    private UssdController ussdController;
    private boolean isProcessing = false;
    
    // Statistics
    private int totalProcessed = 0;
    private int totalFailed = 0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "GatewayService created");
        
        // Initialize queue
        if (transactionQueue == null) {
            transactionQueue = new TransactionQueue(100);
        }
        
        // Initialize USSD controller
        ussdController = new UssdController(this);
        
        // Create worker thread for background processing
        workerThread = new HandlerThread("GatewayWorkerThread");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Create notification channel
        createNotificationChannel();
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Gateway service started", 0));
        
        // Start processing queue
        startQueueProcessor();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "GatewayService started");
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "GatewayService destroyed");
        
        // Stop worker thread
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        
        // Cleanup USSD controller
        if (ussdController != null) {
            ussdController.cleanup();
        }
    }
    
    /**
     * Static method to enqueue transaction from SmsReceiver
     */
    public static boolean enqueue(Context context, Transaction transaction) {
        if (transaction == null) {
            Log.w(TAG, "Cannot enqueue null transaction");
            return false;
        }
        
        // Initialize queue if needed
        if (transactionQueue == null) {
            transactionQueue = new TransactionQueue(100);
        }
        
        // Add to queue
        boolean success = transactionQueue.add(transaction);
        
        if (success) {
            Log.i(TAG, "Transaction enqueued: " + transaction.reference);
            
            // Start service if not running
            Intent serviceIntent = new Intent(context, GatewayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
        
        return success;
    }
    
    /**
     * Start queue processor loop
     */
    private void startQueueProcessor() {
        workerHandler.post(new Runnable() {
            @Override
            public void run() {
                processQueue();
                
                // Schedule next processing cycle
                workerHandler.postDelayed(this, PROCESSING_INTERVAL);
            }
        });
    }
    
    /**
     * Process transactions from queue
     */
    private void processQueue() {
        if (isProcessing) {
            Log.d(TAG, "Already processing a transaction, skipping...");
            return;
        }
        
        if (transactionQueue.isEmpty()) {
            updateNotification("Waiting for transactions", transactionQueue.size());
            return;
        }
        
        Transaction transaction = transactionQueue.next();
        if (transaction == null) {
            return;
        }
        
        isProcessing = true;
        transaction.status = "PROCESSING";
        
        Log.i(TAG, "Processing transaction: " + transaction);
        updateNotification("Processing: " + transaction.promo, transactionQueue.size());
        
        try {
            // Determine which SIM to use
            int simSlot = SimRouter.getSimSlotForNetwork(this, transaction.network);
            
            if (simSlot < 0) {
                throw new Exception("No SIM available for network: " + transaction.network);
            }
            
            // Execute USSD transaction
            boolean success = ussdController.executeReload(transaction, simSlot);
            
            if (success) {
                transaction.status = "SUCCESS";
                transactionQueue.recordSuccess();
                totalProcessed++;
                Log.i(TAG, "Transaction successful: " + transaction.reference);
            } else {
                throw new Exception("USSD execution failed");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Transaction failed: " + transaction.reference, e);
            transaction.status = "FAILED";
            transaction.errorMessage = e.getMessage();
            transaction.retryCount++;
            
            // Retry logic
            if (transaction.retryCount < 3) {
                Log.i(TAG, "Requeueing transaction for retry (" + transaction.retryCount + "/3)");
                transactionQueue.add(transaction);
            } else {
                Log.e(TAG, "Transaction failed after 3 retries: " + transaction.reference);
                transactionQueue.recordFailure();
                totalFailed++;
            }
        } finally {
            isProcessing = false;
        }
        
        // Update notification with stats
        String status = String.format("Processed: %d | Failed: %d | Queue: %d",
                totalProcessed, totalFailed, transactionQueue.size());
        updateNotification(status, transactionQueue.size());
    }
    
    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Gateway Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("SMS Gateway automation service");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Create notification
     */
    private Notification createNotification(String content, int queueSize) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Gateway Active")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    
    /**
     * Update notification content
     */
    private void updateNotification(final String content, final int queueSize) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, createNotification(content, queueSize));
                }
            }
        });
    }
    
    /**
     * Get queue statistics
     */
    public static String getQueueStats() {
        if (transactionQueue != null) {
            return transactionQueue.getStats();
        }
        return "Queue not initialized";
    }
}
