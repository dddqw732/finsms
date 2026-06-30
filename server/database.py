import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "transactions.db")

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            amount REAL NOT NULL,
            currency TEXT NOT NULL,
            sender TEXT NOT NULL,
            receiver TEXT NOT NULL,
            provider TEXT NOT NULL,
            transaction_id TEXT,
            timestamp TEXT NOT NULL,
            type TEXT NOT NULL,
            raw_sms TEXT NOT NULL
        )
    """)
    conn.commit()
    conn.close()

def get_db_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def insert_transaction(amount, currency, sender, receiver, provider, transaction_id, timestamp, type_, raw_sms):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    # Check if transaction ID already exists to avoid duplicates
    if transaction_id:
        cursor.execute("SELECT id FROM transactions WHERE transaction_id = ?", (transaction_id,))
        if cursor.fetchone():
            conn.close()
            return None # Duplicate
            
    cursor.execute("""
        INSERT INTO transactions (amount, currency, sender, receiver, provider, transaction_id, timestamp, type, raw_sms)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (amount, currency, sender, receiver, provider, transaction_id, timestamp, type_, raw_sms))
    
    txn_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return txn_id

def get_transactions(search=None, type_=None, provider=None, sort_by="timestamp", sort_order="desc"):
    conn = get_db_connection()
    cursor = conn.cursor()
    
    query = "SELECT * FROM transactions WHERE 1=1"
    params = []
    
    if search:
        query += " AND (sender LIKE ? OR receiver LIKE ? OR transaction_id LIKE ? OR provider LIKE ?)"
        search_param = f"%{search}%"
        params.extend([search_param, search_param, search_param, search_param])
        
    if type_:
        query += " AND type = ?"
        params.append(type_)
        
    if provider:
        query += " AND provider = ?"
        params.append(provider)
        
    # Prevent SQL injection on sorting
    allowed_sort_fields = {"timestamp", "amount", "provider", "sender", "receiver"}
    if sort_by not in allowed_sort_fields:
        sort_by = "timestamp"
        
    sort_order_str = "DESC" if sort_order.lower() == "desc" else "ASC"
    
    query += f" ORDER BY {sort_by} {sort_order_str}"
    
    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]
