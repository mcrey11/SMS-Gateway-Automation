from flask import Flask, request, jsonify, abort
import requests
import os
import logging
import re
from functools import wraps

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Configuration from environment variables
ANDROID_GATEWAY_URL = os.getenv('ANDROID_GATEWAY_URL', 'http://192.168.1.50:8080/reload')
API_KEY = os.getenv('API_KEY', 'dev-key-change-in-production')
REQUEST_TIMEOUT = int(os.getenv('REQUEST_TIMEOUT', '30'))

# Validation patterns
PHONE_PATTERN = re.compile(r'^09\d{9}$')

def require_api_key(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        provided_key = request.headers.get('X-API-Key')
        if provided_key != API_KEY:
            logger.warning(f"Unauthorized access attempt from {request.remote_addr}")
            abort(401, description="Invalid API key")
        return f(*args, **kwargs)
    return decorated_function

def validate_message(text):
    """Validate and parse reload message format: MSISDN PROMO AMOUNT"""
    if not text or not isinstance(text, str):
        return None, "Message text is required"
    
    parts = text.strip().split()
    if len(parts) != 3:
        return None, "Invalid format. Expected: MSISDN PROMO AMOUNT (e.g., 09171234567 GIGA99 99)"
    
    msisdn, promo, amount_str = parts
    
    # Validate phone number
    if not PHONE_PATTERN.match(msisdn):
        return None, f"Invalid phone number format: {msisdn}. Must be 09XXXXXXXXX"
    
    # Validate promo code
    if not promo.isalnum() or len(promo) > 20:
        return None, f"Invalid promo code: {promo}"
    
    # Validate amount
    try:
        amount = int(amount_str)
        if amount <= 0 or amount > 10000:
            return None, f"Invalid amount: {amount}. Must be between 1 and 10000"
    except ValueError:
        return None, f"Invalid amount: {amount_str}. Must be a number"
    
    # Determine network from promo code
    network = "SMART" if promo.upper().startswith('G') else "GLOBE"
    
    return {
        "msisdn": msisdn,
        "promo": promo.upper(),
        "amount": amount,
        "network": network
    }, None

@app.route("/health", methods=["GET"])
def health_check():
    """Health check endpoint"""
    return jsonify({"status": "healthy", "service": "messenger-webhook"})

@app.route("/messenger", methods=["POST"])
@require_api_key
def messenger_webhook():
    """Handle incoming Messenger webhook requests"""
    try:
        data = request.json
        if not data:
            logger.error("Empty request body")
            return jsonify({"status": "error", "message": "Empty request body"}), 400
        
        message_text = data.get("message")
        if not message_text:
            logger.error("Missing 'message' field in request")
            return jsonify({"status": "error", "message": "Missing 'message' field"}), 400
        
        logger.info(f"Received message: {message_text}")
        
        # Validate and parse message
        parsed, error = validate_message(message_text)
        if error:
            logger.warning(f"Validation failed: {error}")
            return jsonify({"status": "invalid_format", "message": error}), 400
        
        logger.info(f"Parsed transaction: {parsed}")
        
        # Forward to Android gateway
        try:
            response = requests.post(
                ANDROID_GATEWAY_URL,
                json=parsed,
                timeout=REQUEST_TIMEOUT,
                headers={'Content-Type': 'application/json'}
            )
            response.raise_for_status()
            gateway_response = response.json()
            
            logger.info(f"Gateway response: {gateway_response}")
            return jsonify({
                "status": "success",
                "message": "Transaction queued",
                "gateway": gateway_response
            }), 200
            
        except requests.exceptions.Timeout:
            logger.error(f"Gateway timeout after {REQUEST_TIMEOUT}s")
            return jsonify({
                "status": "error",
                "message": "Gateway timeout"
            }), 504
            
        except requests.exceptions.ConnectionError as e:
            logger.error(f"Cannot connect to gateway: {e}")
            return jsonify({
                "status": "error",
                "message": "Gateway unavailable"
            }), 503
            
        except requests.exceptions.HTTPError as e:
            logger.error(f"Gateway HTTP error: {e}")
            return jsonify({
                "status": "error",
                "message": f"Gateway error: {response.status_code}"
            }), 502
            
        except ValueError as e:
            logger.error(f"Invalid JSON response from gateway: {e}")
            return jsonify({
                "status": "error",
                "message": "Invalid gateway response"
            }), 502
    
    except Exception as e:
        logger.exception(f"Unexpected error: {e}")
        return jsonify({
            "status": "error",
            "message": "Internal server error"
        }), 500

if __name__ == '__main__':
    logger.info(f"Starting Messenger Webhook Server")
    logger.info(f"Gateway URL: {ANDROID_GATEWAY_URL}")
    logger.info(f"Request timeout: {REQUEST_TIMEOUT}s")
    
    # Run in production mode
    app.run(
        host='0.0.0.0',
        port=int(os.getenv('PORT', '5000')),
        debug=os.getenv('DEBUG', 'False').lower() == 'true'
    )
