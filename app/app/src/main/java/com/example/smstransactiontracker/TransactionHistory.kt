package com.example.smstransactiontracker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TransactionHistory {
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    fun addTransaction(t: Transaction) {
        _transactions.value = listOf(t) + _transactions.value
    }
    
    fun clear() {
        _transactions.value = emptyList()
    }
}
