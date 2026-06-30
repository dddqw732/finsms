# SMS Transaction Tracker

A real-time financial SMS tracking system: an **Android app** that reads incoming payment SMS messages and forwards parsed transaction data to a **FastAPI backend** with live WebSocket push to a **premium web dashboard**.

---

## Project Structure

```
sms_transaction_tracker/
├── app/          → Android (Kotlin / Jetpack Compose) application
└── server/       → Python FastAPI backend + Web dashboard
    ├── main.py          → FastAPI routes, WebSocket manager
    ├── database.py      → SQLite helpers
    ├── requirements.txt → Python dependencies
    └── static/
        ├── index.html   → Dashboard HTML
        ├── style.css    → Premium styling
        └── app.js       → Dashboard JS (WebSocket, REST, filtering)
```

---

## Quick Start

### 1. Start the Backend Server

```powershell
cd server
# Using uv (recommended)
$env:Path = "C:\Users\<you>\.local\bin;$env:Path"
uv run --with fastapi --with "uvicorn[standard]" uvicorn main:app --host 0.0.0.0 --port 8000
```

The dashboard will be available at **http://localhost:8000**

### 2. Build & Run the Android App

```powershell
cd app
$javaHome = "C:\Users\<you>\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon\windows\java-runtime-epsilon"
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
& "$javaHome\bin\java.exe" -cp .\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug
```

Or install via Android Studio and run on a device/emulator.

### 3. Configure the App

1. Open the app → **Sync Settings** section
2. Enter your computer's local IP (e.g., `http://192.168.1.x:8000`)
3. Grant SMS permissions when prompted
4. Use the **SMS Simulator Sandbox** to test parsing without a real SIM

> **Emulator Tip**: The default URL `http://10.0.2.2:8000` already routes to `localhost` on the host machine.

---

## How It Works

1. **SMS Received** → `SMSReceiver` (BroadcastReceiver) fires
2. **`TransactionParser`** extracts: amount, currency, sender, receiver, provider, transaction ID, timestamp, type
3. Parsed transaction is **saved locally** (visible in the app list) and **HTTP POST**ed to `/api/transactions`
4. Backend **stores in SQLite** and **broadcasts via WebSocket** to all connected dashboards
5. **Dashboard** slides in the new card with a toast notification in real-time

---

## Dashboard Features

| Feature | Description |
|---|---|
| Real-time updates | WebSocket push from server — no page refresh needed |
| Search | Filter by sender, receiver, provider, or transaction ID |
| Filter by type | All / Sent / Received |
| Filter by provider | M-Pesa, PayPal, Venmo, CashApp, Zelle, Stripe |
| Sort | By date, amount, or provider; ascending/descending |
| Metrics | Live totals: transaction count, total received, total sent |
| Connection status | Animated pulse indicator — green=live, red=reconnecting |

---

## Supported SMS Formats

The parser handles various common formats including:

- `Received USD 120.50 from Jane Doe. Ref: TXN987654321`
- `You sent $45.00 to John Smith via PayPal`
- `MPESA: KES 1,000 sent to +254712345678. Ref ABC123`
- `CashApp: You received $25 from @username`
- Generic patterns with any currency symbol prefix or suffix

---

## Security Notes

- Raw SMS data is stored encrypted-at-rest only as much as SQLite allows. For production, add authentication to the API endpoints.
- The backend accepts connections from any IP on the local network — restrict with firewall rules in production.
- Sensitive financial data should be handled on a private network only.
