from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, HTTPException
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from typing import Optional, List
import database
import json
import asyncio

app = FastAPI(title="SMS Transaction Tracker API")

# Initialize database table
database.init_db()

# Pydantic schema for SMS payload
class TransactionPayload(BaseModel):
    amount: float
    currency: str
    sender: str
    receiver: str
    provider: str
    transaction_id: Optional[str] = None
    timestamp: str
    type: str
    raw_sms: str

# WebSocket Connection Manager
class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: str):
        for connection in self.active_connections:
            try:
                await connection.send_text(message)
            except Exception:
                # Handle dead sockets gracefully
                pass

manager = ConnectionManager()

@app.post("/api/transactions")
async def create_transaction(payload: TransactionPayload):
    # Save to database
    txn_id = database.insert_transaction(
        amount=payload.amount,
        currency=payload.currency,
        sender=payload.sender,
        receiver=payload.receiver,
        provider=payload.provider,
        transaction_id=payload.transaction_id,
        timestamp=payload.timestamp,
        type_=payload.type,
        raw_sms=payload.raw_sms
    )
    
    if txn_id is None:
        raise HTTPException(status_code=400, detail="Transaction already exists (duplicate transaction_id)")
        
    # Package transaction dictionary for WebSocket broadcast
    try:
        txn_data = payload.model_dump()
    except AttributeError:
        txn_data = payload.dict()
    txn_data["id"] = txn_id
    
    # Broadcast to all connected dashboard instances
    await manager.broadcast(json.dumps(txn_data))
    
    return {"status": "success", "id": txn_id}

@app.get("/api/transactions")
def list_transactions(
    search: Optional[str] = None,
    type: Optional[str] = None,
    provider: Optional[str] = None,
    sort_by: str = "timestamp",
    sort_order: str = "desc"
):
    return database.get_transactions(
        search=search,
        type_=type,
        provider=provider,
        sort_by=sort_by,
        sort_order=sort_order
    )

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            # Keep client connection open
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)

# Mount static folder for serving web dashboard assets
app.mount("/", StaticFiles(directory="static", html=True), name="static")
