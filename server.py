from flask import Flask, request, jsonify
import os
import logging
import uuid
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# In-memory queue for demo (replace with database in production)
transaction_queue = []

def generate_reference():
    """Generate unique transaction reference"""
    return f"TXN-{datetime.now().strftime('%Y%m%d')}-{str(uuid.uuid4())[:8].upper()}"

@app.route("/health", methods=["GET"])
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "healthy",
        "service": "android-gateway",
        "queue_size": len(transaction_queue)
    })

@app.route("/reload", methods=["POST"])
def reload():
    """Receive reload transaction request"""
    try:
        data = request.json
        if not data:
            logger.error("Empty request body")
            return jsonify({
                "status": "ERROR",
                "message": "Empty request body"
            }), 400
        
        # Validate required fields
        required_fields = ['msisdn', 'promo', 'amount']
        missing_fields = [field for field in required_fields if field not in data]
        
        if missing_fields:
            logger.error(f"Missing required fields: {missing_fields}")
            return jsonify({
                "status": "ERROR",
                "message": f"Missing required fields: {', '.join(missing_fields)}"
            }), 400
        
        # Validate amount
        try:
            amount = int(data['amount'])
            if amount <= 0:
                raise ValueError("Amount must be positive")
        except (ValueError, TypeError) as e:
            logger.error(f"Invalid amount: {data.get('amount')}")
            return jsonify({
                "status": "ERROR",
                "message": f"Invalid amount: {str(e)}"
            }), 400
        
        # Generate transaction reference
        reference = generate_reference()
        
        # Create transaction record
        transaction = {
            "reference": reference,
            "msisdn": data['msisdn'],
            "promo": data['promo'],
            "amount": amount,
            "network": data.get('network', 'UNKNOWN'),
            "status": "QUEUED",
            "timestamp": datetime.now().isoformat(),
            "remote_addr": request.remote_addr
        }
        
        # Add to queue
        transaction_queue.append(transaction)
        
        logger.info(f"Transaction queued: {reference} - {data['msisdn']} - {data['promo']} - {amount}")
        logger.info(f"Queue size: {len(transaction_queue)}")
        
        return jsonify({
            "status": "QUEUED",
            "reference": reference,
            "queue_position": len(transaction_queue)
        }), 200
    
    except Exception as e:
        logger.exception(f"Unexpected error: {e}")
        return jsonify({
            "status": "ERROR",
            "message": "Internal server error"
        }), 500

@app.route("/queue", methods=["GET"])
def get_queue():
    """Get current transaction queue status"""
    return jsonify({
        "queue_size": len(transaction_queue),
        "transactions": transaction_queue[-10:]  # Last 10 transactions
    })

@app.route("/status/<reference>", methods=["GET"])
def get_status(reference):
    """Get transaction status by reference"""
    for txn in transaction_queue:
        if txn['reference'] == reference:
            return jsonify(txn)
    
    return jsonify({
        "status": "NOT_FOUND",
        "message": f"Transaction {reference} not found"
    }), 404

if __name__ == '__main__':
    logger.info("Starting Android Gateway API Server")
    logger.info(f"Listening on port {os.getenv('PORT', '8080')}")
    
    app.run(
        host="0.0.0.0",
        port=int(os.getenv('PORT', '8080')),
        debug=os.getenv('DEBUG', 'False').lower() == 'true'
    )
