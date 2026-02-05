# SMS-Gateway-Automation

## Android Gateway App — Realistic Design
This app will function as a telecom transaction gateway device, not a normal mobile app.
It will run continuously on a dedicated Android phone.

###1. SMS Gateway Receiver

Captures reload requests sent via SMS.
Uses:
- BroadcastReceiver
- SmsManager
- Local transaction queue

Flow:
SMS received → Parse → Validate → Queue transaction

### 2. USSD Automation Engine
Executes Smart and Globe reload menus.\
Handles:
- Dialing USSD\
- Entering mobile number\
- Entering promo\
- Confirmation\
- Parsing result

This is the heart of the system.

### 3. Gateway API (Optional but Recommended)
Allows:
- Messenger bot server to send requests\
- Monitoring system health\
- Viewing logs remotely

Typical endpoints:
> POST /reload\
GET /status\
GET /queue
> 

## Internal App Architecture

> Android Gateway App\
│\
├── SMSReceiver\
├── RequestParser\
├── SenderValidator\
├── TransactionQueue\
├── USSDController\
├── SIMRouter\
├── WatchdogService\
└── StatusReporter
>

# Java
Java is fully supported for Android development.
In fact, Java is still one of the most stable choices for:

> SMS interception\
Telephony control\
Background services\
Accessibility automation\
Dual-SIM handling
>

You can build the entire gateway using Java with:
- Android Studio\
- Android SDK\
- Gradle

All required APIs are available in Java:

- BroadcastReceiver\
- SmsMessage\
- TelephonyManager\
- SubscriptionManager\
- AccessibilityService\
- ForegroundService

So Java works perfectly for the Android gateway app.

# Python
Python cannot directly replace Java/Kotlin for Android system-level features.

Here’s why:

Android telephony features like:\
USSD dialing\
SMS receiving\
SIM control\
Accessibility services\
are only exposed through the Android SDK, which is Java/Kotlin-based.
Python frameworks (like Kivy, BeeWare, Chaquopy) do not reliably support telephony automation.
So Python alone is not suitable for the gateway device app.


# Where Python Does Make Sense

Python is excellent for the server/controller layer of your system.

Example architecture:

Python Server\
   │\
   │ REST / WebSocket\
   ▼\
Android Gateway App (Java)\
   │\
   ▼\
USSD (*343#, *100#)\


Python can handle:
- Facebook Messenger webhook\
- Sender validation database\
- Wallet logic\
- Logging\
- Reporting\
- API endpoints\
- Transaction routing\

## Recommended Stack for Your Project
Based on your requirements (SMS + Messenger + USSD gateway):
Android Gateway\
Language:
- Java (or Kotlin)\
- Responsibilities:\
- SMS receiver\
- USSD automation\
- SIM routing\
- Transaction queue\
- Watchdog

## Server Layer

Language:
- Python

Responsibilities:
- Messenger bot webhook\
- Registered sender database\
- Wallet/balance logic\
- Reporting\
- Dashboard\
- API controller

## Realistic Project Split

### Android (Java)
Gateway device controller
### Python (Flask/FastAPI)
Business logic + Messenger integration

This separation makes the system much more stable and scalable.

## Important Design Insight

Trying to build everything inside Android will make the system fragile.\
The robust pattern is:

> Android = hardware gateway\
Python = transaction brain
>

## We’ll do four things in order:

> 1. Android gateway project structure (Java)
> 2. Python Messenger webhook server
> 3. API contract between Android and Python
> 4. Starter code templates for both
>

## 1. Android Gateway App — Java Project Structure

This is structured like a telephony service app, not a normal UI app.

android-gateway/\
│\
├── app/\
│   ├── src/main/java/com/gateway/\
│   │\
│   ├── MainActivity.java\
│   │\
│   ├── sms/\
│   │   └── SmsReceiver.java\
│   │\
│   ├── parser/\
│   │   └── ReloadParser.java\
│   │\
│   ├── queue/\
│   │   ├── TransactionQueue.java\
│   │   └── Transaction.java\
│   │\
│   ├── ussd/\
│   │   ├── UssdController.java\
│   │   └── SimRouter.java\
│   │\
│   ├── service/\
│   │   ├── GatewayService.java\
│   │   └── WatchdogService.java\
│   │\
│   ├── api/\
│   │   └── LocalApiServer.java\
│   │\
│   └── db/\
│       └── DatabaseHelper.java\
│\
└── AndroidManifest.xml\

## Responsibilities of Each Component

- SmsReceiver\
- Captures SMS reload requests.\
- ReloadParser

Validates:
>09171234567 GIGA99 99
>

### TransactionQueue

Ensures:
- FIFO execution\
- One USSD session per SIM

UssdController
Handles:
> *343#\
*100#
>

SimRouter
Maps:
> SMART → SIM1\
GLOBE → SIM2
>

### GatewayService
Foreground service that keeps gateway alive.

### WatchdogService

Recovers from:
- stuck USSD
- network failure
- app crash

# 2. Python Messenger Webhook Server

We’ll use Flask because it’s simple and reliable.

Install:
> pip install flask requests
>

messenger_server.py
> from flask import Flask, request, jsonify\
import requests\
app = Flask(__name__)\
ANDROID_GATEWAY_URL = "http://192.168.1.50:8080/reload"\
def parse_message(text):\
    parts = text.strip().split(" ")\
    if len(parts) != 3:\
        return None\
return {\
        "msisdn": parts[0],\
        "promo": parts[1],\
        "amount": parts[2]\
    }\
@app.route("/messenger", methods=["POST"])\
def messenger_webhook():\
    data = request.json\
    message_text = data["message"]\
parsed = parse_message(message_text)\
    if not parsed:\
        return jsonify({"status": "invalid_format"})\
r = requests.post(ANDROID_GATEWAY_URL, json=parsed)\
    return jsonify({"status": "sent_to_gateway", "gateway": r.json()})\
app.run(port=5000)\
>

## 3. API Contract (Android ↔ Python)

This is important — treat this like a real interface spec.

Reload Request (Python → Android)\
POST /reload
>Content-Type: application/json
>

Body
> {
  "msisdn": "09171234567",
  "promo": "GIGA99",
  "amount": 99,
  "network": "SMART"
}
>

Android Response
> {
  "status": "QUEUED",
  "reference": "TXN1023"
}
>

Status Callback (Optional)\
POST /status
> {
  "reference": "TXN1023",
  "status": "SUCCESS"
}
>

## 4. Starter Code Templates

Now we bootstrap the gateway.

Android Starter Code (Java)
Transaction.java
> public class Transaction {
    public String msisdn;
    public String promo;
    public int amount;
    public String network;
}
>

TransactionQueue.java
> import java.util.LinkedList;\
import java.util.Queue;\
public class TransactionQueue {\
    private final Queue<Transaction> queue = new LinkedList<>();\
    public synchronized void add(Transaction t) {\
        queue.add(t);
    }\
    public synchronized Transaction next() {\
        return queue.poll();\
    }\
}\
>

ReloadParser.java

> public class ReloadParser {\
    public static Transaction parse(String sms) {\
        String[] parts = sms.trim().split(" ");\
        if (parts.length != 3) return null;\
        Transaction t = new Transaction();\
        t.msisdn = parts[0];\
        t.promo = parts[1];\
        t.amount = Integer.parseInt(parts[2]);\
        if (t.promo.startsWith("G")) {\
            t.network = "SMART";\
        } else {\
            t.network = "GLOBE";\
        }\
        return t;\
    }\
}\
> 

SmsReceiver.java

> public class SmsReceiver extends BroadcastReceiver {\
    @Override\
    public void onReceive(Context context, Intent intent) {\
        Bundle bundle = intent.getExtras();\
        Object[] pdus = (Object[]) bundle.get("pdus");\
        for (Object pdu : pdus) {\
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu);\
            String body = msg.getMessageBody();\
            Transaction t = ReloadParser.parse(body);\            
            if (t != null) {\
                GatewayService.enqueue(t);\
            }\
        }\
    }\
}\
>

## Python Server Starter\
requirements:
> flask\
requests
>

server.py
> from flask import Flask, request, jsonify\
app = Flask(__name__)\
@app.route("/reload", methods=["POST"])\
def reload():\
    data = request.json\
    print("Reload request:", data)\
    return jsonify({\
        "status": "QUEUED",\
        "reference": "TXN-DEMO"\
    })\
app.run(host="0.0.0.0", port=8080)\
>

## Recommended Build Order

> Follow this sequence:\
Step 1:\
SMS Receiver → Parser → Queue\
Step 2:\
Foreground GatewayService\
Step 3:\
USSD controller\
Step 4:\
Local API server\
Step 5:\
Messenger webhook
>




