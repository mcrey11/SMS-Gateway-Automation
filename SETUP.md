# SMS Gateway Automation - Setup & Deployment Guide

## Project Overview

This project implements a complete SMS Gateway automation system with:
- **Android App** (Java): Hardware gateway for SMS receiving and USSD automation
- **Python Server**: Messenger webhook integration and business logic

## Quick Start

### Python Server Setup

1. **Install dependencies**:
```bash
pip install -r requirements.txt
```

2. **Configure environment**:
```bash
cp .env.example .env
# Edit .env with your configuration
```

3. **Run the messenger webhook server**:
```bash
python messenger_server.py
```

4. **Run the Android gateway mock server** (for testing):
```bash
python server.py
```

### Android App Setup

1. **Open in Android Studio**:
   - Import the project directory
   - Sync Gradle dependencies

2. **Configure permissions**:
   - Grant SMS permissions
   - Grant Phone permissions
   - Enable Accessibility Service for USSD automation

3. **Build and deploy**:
   - Connect Android device via USB
   - Click "Run" in Android Studio
   - Install on dedicated gateway phone

## Architecture

```
┌─────────────────────┐
│ Facebook Messenger  │
│      Bot/Webhook    │
└──────────┬──────────┘
           │ HTTP POST
           ▼
┌─────────────────────┐
│ messenger_server.py │
│  (Port 5000)        │
└──────────┬──────────┘
           │ HTTP POST /reload
           ▼
┌─────────────────────┐
│  Android Gateway    │
│  LocalApiServer     │
│  (Port 8080)        │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  TransactionQueue   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  UssdController     │
│  (*343#, *100#)     │
└─────────────────────┘
```

## Components

### Python Components

#### messenger_server.py
- Receives Messenger webhook requests
- Validates reload format (MSISDN PROMO AMOUNT)
- Forwards to Android gateway
- **Features**: 
  - Request validation
  - Error handling
  - Logging
  - API key authentication

#### server.py
- Mock Android gateway for testing
- Queue management
- Status tracking
- **Features**:
  - Transaction queue
  - Health checks
  - Statistics

### Android Components

#### SmsReceiver.java
- Intercepts incoming SMS
- Parses reload requests
- Queues transactions
- **Format**: `09171234567 GIGA99 99`

#### ReloadParser.java
- Validates SMS format
- Extracts transaction details
- Network detection (Smart/Globe)

#### TransactionQueue.java
- Thread-safe FIFO queue
- Capacity limits (100 transactions)
- Statistics tracking

#### GatewayService.java
- Foreground service (keeps running)
- Processes transaction queue
- Manages USSD execution
- Auto-restart on failure

#### UssdController.java
- USSD automation engine
- Smart: `*343#` menu navigation
- Globe: `*100#` menu navigation
- Requires AccessibilityService

#### SimRouter.java
- Dual-SIM management
- Network-to-SIM mapping
- Automatic carrier detection

#### LocalApiServer.java
- HTTP API server (port 8080)
- Endpoints:
  - `POST /reload` - Queue transaction
  - `GET /status` - Check status
  - `GET /queue` - Queue stats
  - `GET /health` - Health check

#### MainActivity.java
- UI for monitoring
- Service control
- Queue visualization
- SIM status display

## API Endpoints

### Messenger Server (Port 5000)

#### POST /messenger
Queue reload request from Messenger.

**Headers**:
```
X-API-Key: your-api-key
Content-Type: application/json
```

**Request**:
```json
{
  "message": "09171234567 GIGA99 99"
}
```

**Response**:
```json
{
  "status": "success",
  "message": "Transaction queued",
  "gateway": {
    "status": "QUEUED",
    "reference": "TXN-20260205-A1B2C3D4"
  }
}
```

### Android Gateway (Port 8080)

#### POST /reload
Queue reload transaction.

**Request**:
```json
{
  "msisdn": "09171234567",
  "promo": "GIGA99",
  "amount": 99,
  "network": "SMART"
}
```

**Response**:
```json
{
  "status": "QUEUED",
  "reference": "TXN-20260205-A1B2C3D4",
  "queue_position": 3
}
```

#### GET /health
Health check and system status.

**Response**:
```json
{
  "status": "healthy",
  "service": "android-gateway",
  "timestamp": 1738713600000,
  "sim_info": "Active SIMs: 2\nSlot 0: SMART..."
}
```

## Configuration

### Environment Variables (.env)

```bash
# Messenger Server
PORT=5000
ANDROID_GATEWAY_URL=http://192.168.1.50:8080/reload
API_KEY=your-secure-api-key
REQUEST_TIMEOUT=30
DEBUG=False

# Limits
MAX_AMOUNT=10000
MIN_AMOUNT=1
```

### Android Permissions

Required permissions in AndroidManifest.xml:
- `RECEIVE_SMS` - Receive SMS messages
- `READ_SMS` - Read SMS content
- `SEND_SMS` - Send SMS (if needed)
- `CALL_PHONE` - Dial USSD codes
- `READ_PHONE_STATE` - Read SIM information
- `INTERNET` - API server
- `FOREGROUND_SERVICE` - Keep service running

**Important**: Must enable Accessibility Service manually in Android settings for USSD automation.

## Security Considerations

### Implemented
✅ API key authentication between servers
✅ Input validation (phone numbers, amounts)
✅ Request timeout handling
✅ Error logging without sensitive data
✅ Environment variable configuration

### Recommended for Production
⚠️ HTTPS/TLS encryption
⚠️ Certificate pinning
⚠️ Rate limiting
⚠️ Transaction signing
⚠️ Sender whitelist database
⚠️ Monitoring and alerting
⚠️ Backup and disaster recovery

## Testing

### Test Messenger Server
```bash
curl -X POST http://localhost:5000/messenger \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-key-change-in-production" \
  -d '{"message": "09171234567 GIGA99 99"}'
```

### Test Android Gateway
```bash
curl -X POST http://192.168.1.50:8080/reload \
  -H "Content-Type: application/json" \
  -d '{
    "msisdn": "09171234567",
    "promo": "GIGA99",
    "amount": 99,
    "network": "SMART"
  }'
```

### Check Health
```bash
curl http://192.168.1.50:8080/health
```

## Deployment

### Python Server
For production, use Gunicorn:
```bash
gunicorn -w 4 -b 0.0.0.0:5000 messenger_server:app
```

### Android App
1. Build release APK in Android Studio
2. Install on dedicated Android phone
3. Configure as gateway device (disable sleep, enable auto-start)
4. Ensure stable power and network connection

## Troubleshooting

### USSD Not Working
- Enable Accessibility Service in Android Settings
- Check SIM routing configuration
- Verify USSD codes (*343# for Smart, *100# for Globe)

### Transactions Not Queuing
- Check API server is running (port 8080)
- Verify network connectivity
- Check GatewayService is running (notification should be visible)

### SMS Not Received
- Grant SMS permissions
- Check SmsReceiver is registered in manifest
- Verify SMS format: `09XXXXXXXXX PROMO AMOUNT`

## Monitoring

Check logs:
```bash
# Python
tail -f logs/messenger.log

# Android
adb logcat | grep -E "(GatewayService|UssdController|SmsReceiver)"
```

## Next Steps

1. Implement transaction persistence (database)
2. Add retry mechanism with exponential backoff
3. Implement webhook callbacks for status updates
4. Add monitoring dashboard
5. Set up automated testing
6. Implement wallet/balance management
7. Add sender whitelist verification

## Support

For issues and questions, check the logs and verify:
- All permissions granted
- Accessibility service enabled
- Network connectivity
- SIM cards active and registered
- Environment variables configured

---

**Last Updated**: February 5, 2026
**Version**: 1.0.0
