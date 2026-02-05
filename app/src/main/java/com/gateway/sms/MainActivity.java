package com.gateway.sms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * Main activity for SMS Gateway app
 * Provides basic UI for monitoring and controlling the gateway
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    
    private TextView statusText;
    private TextView queueText;
    private TextView simInfoText;
    private Button startServiceButton;
    private Button stopServiceButton;
    private LocalApiServer apiServer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize UI components
        statusText = findViewById(R.id.statusText);
        queueText = findViewById(R.id.queueText);
        simInfoText = findViewById(R.id.simInfoText);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
        
        // Set up button listeners
        startServiceButton.setOnClickListener(v -> startGatewayService());
        stopServiceButton.setOnClickListener(v -> stopGatewayService());
        
        // Display SIM information
        updateSimInfo();
        
        // Start API server
        startApiServer();
        
        // Update UI periodically
        startUiUpdates();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop API server
        if (apiServer != null) {
            apiServer.stop();
        }
    }
    
    /**
     * Start the gateway foreground service
     */
    private void startGatewayService() {
        Intent serviceIntent = new Intent(this, GatewayService.class);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        Toast.makeText(this, "Gateway service started", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Gateway service started");
    }
    
    /**
     * Stop the gateway foreground service
     */
    private void stopGatewayService() {
        Intent serviceIntent = new Intent(this, GatewayService.class);
        stopService(serviceIntent);
        
        Toast.makeText(this, "Gateway service stopped", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Gateway service stopped");
    }
    
    /**
     * Start local API server
     */
    private void startApiServer() {
        try {
            apiServer = new LocalApiServer(this, 8080);
            apiServer.start();
            
            Toast.makeText(this, "API server started on port 8080", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "API server started");
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to start API server", e);
            Toast.makeText(this, "Failed to start API server: " + e.getMessage(), 
                    Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Update SIM information display
     */
    private void updateSimInfo() {
        String simInfo = SimRouter.getSimInfo(this);
        simInfoText.setText(simInfo);
    }
    
    /**
     * Start periodic UI updates
     */
    private void startUiUpdates() {
        final android.os.Handler handler = new android.os.Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateQueueStatus();
                handler.postDelayed(this, 2000); // Update every 2 seconds
            }
        }, 2000);
    }
    
    /**
     * Update queue status display
     */
    private void updateQueueStatus() {
        String stats = GatewayService.getQueueStats();
        queueText.setText(stats);
        
        statusText.setText("Gateway Status: ACTIVE");
    }
}
