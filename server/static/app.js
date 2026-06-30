'use strict';

// ─── State ───────────────────────────────────────────────────────────────────
let allTransactions = [];
let filteredTransactions = [];
let searchQuery = '';
let filterType = '';
let filterProvider = '';
let sortBy = 'timestamp';
let sortOrder = 'desc';
let ws = null;
let wsRetryCount = 0;

// ─── DOM References ──────────────────────────────────────────────────────────
const ledgerList     = document.getElementById('ledger-list');
const ledgerLoader   = document.getElementById('ledger-loader');
const ledgerEmpty    = document.getElementById('ledger-empty');
const searchInput    = document.getElementById('search-input');
const providerFilter = document.getElementById('provider-filter');
const sortBySelect   = document.getElementById('sort-by');
const sortOrderBtn   = document.getElementById('sort-order-btn');
const typeFilterBtns = document.querySelectorAll('.filter-btn[data-type]');
const resetBtn       = document.getElementById('reset-btn');
const clearDbBtn     = document.getElementById('clear-db-btn');
const pulse          = document.getElementById('connection-pulse');
const connText       = document.getElementById('connection-text');
const liveTimeEl     = document.getElementById('live-time');

// ─── Live Clock ──────────────────────────────────────────────────────────────
function updateClock() {
    const now = new Date();
    liveTimeEl.textContent = now.toLocaleString('en-US', {
        month: 'long', day: 'numeric', year: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
}
setInterval(updateClock, 1000);
updateClock();

// ─── WebSocket Connection ────────────────────────────────────────────────────
function connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;
    
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        wsRetryCount = 0;
        pulse.className = 'pulse-indicator connected';
        connText.textContent = 'Live — Syncing in Real-Time';
        connText.style.color = 'var(--success)';
    };

    ws.onmessage = (event) => {
        const txn = JSON.parse(event.data);
        handleNewTransaction(txn);
    };

    ws.onclose = () => {
        pulse.className = 'pulse-indicator disconnected';
        connText.textContent = 'Reconnecting...';
        connText.style.color = 'var(--danger)';
        
        // Exponential backoff with max 30s
        const retryDelay = Math.min(1000 * Math.pow(2, wsRetryCount), 30000);
        wsRetryCount++;
        setTimeout(connectWebSocket, retryDelay);
    };

    ws.onerror = () => {
        ws.close();
    };
}

// ─── Handle New Real-Time Transaction ───────────────────────────────────────
function handleNewTransaction(txn) {
    // Prepend to local array
    allTransactions.unshift(txn);
    applyFilters();
    updateMetrics();
    showToast(txn);
}

// ─── Fetch All From REST API ─────────────────────────────────────────────────
async function fetchTransactions() {
    showLoader();
    try {
        const params = new URLSearchParams({
            sort_by: sortBy,
            sort_order: sortOrder
        });
        if (searchQuery)    params.set('search', searchQuery);
        if (filterType)     params.set('type', filterType);
        if (filterProvider) params.set('provider', filterProvider);
        
        const res = await fetch(`/api/transactions?${params}`);
        if (!res.ok) throw new Error('Failed to fetch');
        
        allTransactions = await res.json();
        applyFilters();
        updateMetrics();
    } catch (err) {
        console.error('Fetch error:', err);
        showEmpty();
    }
}

// ─── Filtering & Sorting (local) ─────────────────────────────────────────────
function applyFilters() {
    let result = [...allTransactions];

    if (searchQuery) {
        const q = searchQuery.toLowerCase();
        result = result.filter(t =>
            (t.sender  || '').toLowerCase().includes(q) ||
            (t.receiver || '').toLowerCase().includes(q) ||
            (t.provider || '').toLowerCase().includes(q) ||
            (t.transaction_id || '').toLowerCase().includes(q)
        );
    }

    if (filterType) {
        result = result.filter(t => t.type === filterType);
    }

    if (filterProvider) {
        result = result.filter(t => t.provider === filterProvider);
    }

    // Sort
    result.sort((a, b) => {
        let valA = a[sortBy] ?? '';
        let valB = b[sortBy] ?? '';
        if (sortBy === 'amount') {
            valA = parseFloat(valA) || 0;
            valB = parseFloat(valB) || 0;
        }
        if (valA < valB) return sortOrder === 'asc' ? -1 : 1;
        if (valA > valB) return sortOrder === 'asc' ? 1 : -1;
        return 0;
    });

    filteredTransactions = result;
    renderLedger();
}

// ─── Metrics ─────────────────────────────────────────────────────────────────
function updateMetrics() {
    const totalCount    = allTransactions.length;
    const totalReceived = allTransactions
        .filter(t => t.type === 'Received')
        .reduce((sum, t) => sum + (parseFloat(t.amount) || 0), 0);
    const totalSent = allTransactions
        .filter(t => t.type === 'Sent')
        .reduce((sum, t) => sum + (parseFloat(t.amount) || 0), 0);

    document.getElementById('stat-total-count').textContent = totalCount;
    document.getElementById('stat-total-received').textContent = `$${totalReceived.toFixed(2)}`;
    document.getElementById('stat-total-sent').textContent = `$${totalSent.toFixed(2)}`;

    clearDbBtn.style.display = totalCount > 0 ? '' : 'none';
}

// ─── Rendering Ledger Cards ───────────────────────────────────────────────────
function renderLedger() {
    if (filteredTransactions.length === 0) {
        showEmpty();
        return;
    }
    
    hideLoader();
    ledgerEmpty.classList.add('hidden');
    ledgerList.classList.remove('hidden');
    ledgerList.innerHTML = filteredTransactions.map(renderCard).join('');
}

function renderCard(txn) {
    const isReceived = txn.type === 'Received';
    const typeClass  = isReceived ? 'received' : 'sent';
    
    const arrow = isReceived
        ? `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
               <path d="M19 14l-7 7m0 0l-7-7m7 7V3" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
           </svg>`
        : `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
               <path d="M5 10l7-7m0 0l7 7m-7-7v18" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
           </svg>`;
    
    const counterpart = isReceived
        ? `<span>From: <strong>${escapeHtml(txn.sender)}</strong></span>`
        : `<span>To: <strong>${escapeHtml(txn.receiver)}</strong></span>`;
    
    const txnIdHtml = txn.transaction_id
        ? `<span>ID: ${escapeHtml(txn.transaction_id)}</span>`
        : '';
    
    const formattedTime = formatTimestamp(txn.timestamp);
    const sign = isReceived ? '+' : '−';
    
    return `
        <div class="ledger-card">
            <div class="ledger-main">
                <div class="type-indicator ${typeClass}">${arrow}</div>
                <div class="ledger-details">
                    <div class="ledger-headline">
                        <h4>${isReceived ? 'Received' : 'Sent'}</h4>
                        <span class="badge-provider">${escapeHtml(txn.provider)}</span>
                    </div>
                    <div class="ledger-meta">
                        ${counterpart}
                        ${txnIdHtml}
                    </div>
                </div>
            </div>
            <div class="ledger-amount-area">
                <div class="ledger-amount ${typeClass}">${sign} ${escapeHtml(txn.currency)} ${parseFloat(txn.amount).toFixed(2)}</div>
                <div class="ledger-time">${formattedTime}</div>
            </div>
        </div>`;
}

function formatTimestamp(ts) {
    if (!ts) return '';
    try {
        const d = new Date(ts);
        return d.toLocaleString('en-US', {
            month: 'short', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    } catch {
        return ts;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

// ─── UI State Helpers ─────────────────────────────────────────────────────────
function showLoader() {
    ledgerLoader.classList.remove('hidden');
    ledgerEmpty.classList.add('hidden');
    ledgerList.classList.add('hidden');
}

function hideLoader() {
    ledgerLoader.classList.add('hidden');
}

function showEmpty() {
    hideLoader();
    ledgerEmpty.classList.remove('hidden');
    ledgerList.classList.add('hidden');
}

// ─── Toast Notifications ─────────────────────────────────────────────────────
function showToast(txn) {
    const container = document.getElementById('toast-container');
    const isReceived = txn.type === 'Received';
    const sign = isReceived ? '+' : '−';
    const color = isReceived ? 'var(--success)' : 'var(--danger)';
    
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.innerHTML = `
        <div style="width:8px;height:8px;border-radius:50%;background:${color};flex-shrink:0;"></div>
        <div>
            <strong>${txn.type}</strong> via ${escapeHtml(txn.provider)}
            <br><span style="color:#94a3b8;">${sign} ${escapeHtml(txn.currency)} ${parseFloat(txn.amount).toFixed(2)}</span>
        </div>`;
    
    container.prepend(toast);
    setTimeout(() => {
        toast.style.transition = 'opacity 0.4s ease, transform 0.4s ease';
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(8px)';
        setTimeout(() => toast.remove(), 400);
    }, 4000);
}

// ─── Event Listeners ──────────────────────────────────────────────────────────
searchInput.addEventListener('input', (e) => {
    searchQuery = e.target.value.trim();
    applyFilters();
});

providerFilter.addEventListener('change', (e) => {
    filterProvider = e.target.value;
    fetchTransactions(); // Re-fetch with server-side filter
});

sortBySelect.addEventListener('change', (e) => {
    sortBy = e.target.value;
    applyFilters();
});

sortOrderBtn.addEventListener('click', () => {
    sortOrder = sortOrder === 'desc' ? 'asc' : 'desc';
    const icon = document.getElementById('sort-desc-icon');
    if (sortOrder === 'asc') {
        icon.innerHTML = `<path d="M12 19V5M12 5L5 12M12 5L19 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`;
    } else {
        icon.innerHTML = `<path d="M12 5V19M12 19L19 12M12 19L5 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`;
    }
    applyFilters();
});

typeFilterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        typeFilterBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        filterType = btn.dataset.type;
        applyFilters();
    });
});

resetBtn.addEventListener('click', () => {
    searchQuery = '';
    filterType = '';
    filterProvider = '';
    sortBy = 'timestamp';
    sortOrder = 'desc';
    searchInput.value = '';
    providerFilter.value = '';
    sortBySelect.value = 'timestamp';
    typeFilterBtns.forEach(b => b.classList.remove('active'));
    typeFilterBtns[0].classList.add('active');
    fetchTransactions();
});

clearDbBtn.addEventListener('click', async () => {
    if (!confirm('Clear all transactions from the database?')) return;
    // We don't have a delete endpoint yet — just clear locally
    allTransactions = [];
    applyFilters();
    updateMetrics();
});

// ─── Bootstrap ───────────────────────────────────────────────────────────────
fetchTransactions().then(() => {
    connectWebSocket();
});
