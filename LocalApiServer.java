import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Local HTTP API server for receiving reload requests
 * Runs on Android device to accept transactions from Python server
 */
public class LocalApiServer {
    private static final String TAG = "LocalApiServer";
    private static final int DEFAULT_PORT = 8080;
    
    private HttpServer server;
    private Context context;
    private int port;
    
    public LocalApiServer(Context context) {
        this(context, DEFAULT_PORT);
    }
    
    public LocalApiServer(Context context, int port) {
        this.context = context;
        this.port = port;
    }
    
    /**
     * Start the API server
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Register endpoints
        server.createContext("/reload", new ReloadHandler());
        server.createContext("/status", new StatusHandler());
        server.createContext("/queue", new QueueHandler());
        server.createContext("/health", new HealthHandler());
        
        // Use thread pool
        server.setExecutor(Executors.newFixedThreadPool(4));
        
        // Start server
        server.start();
        
        Log.i(TAG, "API Server started on port " + port);
    }
    
    /**
     * Stop the API server
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            Log.i(TAG, "API Server stopped");
        }
    }
    
    /**
     * Handle /reload endpoint - Queue new transaction
     */
    private class ReloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }
            
            try {
                // Read request body
                String requestBody = readRequestBody(exchange);
                Log.i(TAG, "Reload request received: " + requestBody);
                
                // Parse JSON
                JSONObject json = new JSONObject(requestBody);
                
                // Validate required fields
                if (!json.has("msisdn") || !json.has("promo") || !json.has("amount")) {
                    sendResponse(exchange, 400, createErrorResponse("Missing required fields"));
                    return;
                }
                
                // Create transaction
                Transaction transaction = new Transaction();
                transaction.msisdn = json.getString("msisdn");
                transaction.promo = json.getString("promo");
                transaction.amount = json.getInt("amount");
                transaction.network = json.optString("network", "UNKNOWN");
                
                // Enqueue transaction
                boolean success = GatewayService.enqueue(context, transaction);
                
                if (success) {
                    JSONObject response = new JSONObject();
                    response.put("status", "QUEUED");
                    response.put("reference", transaction.reference);
                    response.put("timestamp", transaction.timestamp);
                    
                    sendResponse(exchange, 200, response.toString());
                    Log.i(TAG, "Transaction queued: " + transaction.reference);
                } else {
                    sendResponse(exchange, 503, createErrorResponse("Queue is full"));
                }
                
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON", e);
                sendResponse(exchange, 400, createErrorResponse("Invalid JSON format"));
            } catch (Exception e) {
                Log.e(TAG, "Error processing request", e);
                sendResponse(exchange, 500, createErrorResponse("Internal server error"));
            }
        }
    }
    
    /**
     * Handle /status endpoint - Get transaction status
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }
            
            try {
                // Extract reference from query string
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.startsWith("reference=")) {
                    sendResponse(exchange, 400, createErrorResponse("Missing reference parameter"));
                    return;
                }
                
                String reference = query.substring("reference=".length());
                
                // Note: This requires TransactionQueue to support lookup by reference
                // For now, return basic response
                JSONObject response = new JSONObject();
                response.put("reference", reference);
                response.put("status", "UNKNOWN");
                response.put("message", "Status tracking not fully implemented");
                
                sendResponse(exchange, 200, response.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting status", e);
                sendResponse(exchange, 500, createErrorResponse("Internal server error"));
            }
        }
    }
    
    /**
     * Handle /queue endpoint - Get queue statistics
     */
    private class QueueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }
            
            try {
                String stats = GatewayService.getQueueStats();
                
                JSONObject response = new JSONObject();
                response.put("stats", stats);
                response.put("timestamp", System.currentTimeMillis());
                
                sendResponse(exchange, 200, response.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting queue stats", e);
                sendResponse(exchange, 500, createErrorResponse("Internal server error"));
            }
        }
    }
    
    /**
     * Handle /health endpoint - Health check
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject response = new JSONObject();
                response.put("status", "healthy");
                response.put("service", "android-gateway");
                response.put("timestamp", System.currentTimeMillis());
                response.put("sim_info", SimRouter.getSimInfo(context));
                
                sendResponse(exchange, 200, response.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "Error in health check", e);
                sendResponse(exchange, 500, createErrorResponse("Internal server error"));
            }
        }
    }
    
    /**
     * Read request body from HTTP exchange
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody()));
        
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        
        return body.toString();
    }
    
    /**
     * Send HTTP response
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes("UTF-8");
        
        // Set headers
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        // Send response
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
    
    /**
     * Create JSON error response
     */
    private String createErrorResponse(String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("status", "ERROR");
            error.put("message", message);
            return error.toString();
        } catch (JSONException e) {
            return "{\"status\":\"ERROR\",\"message\":\"" + message + "\"}";
        }
    }
}
