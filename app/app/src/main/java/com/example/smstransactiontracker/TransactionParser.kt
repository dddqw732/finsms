package com.example.smstransactiontracker

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

data class Transaction(
    val amount: Double,
    val currency: String,
    val sender: String,
    val receiver: String,
    val provider: String,
    val transactionId: String?,
    val timestamp: String, // ISO 8601 format
    val type: String,      // "Sent" or "Received"
    val rawSms: String
)

object TransactionParser {
    fun parse(smsBody: String, senderNumber: String): Transaction? {
        val normalizedBody = smsBody.replace("\n", " ").trim()
        
        // Identify the payment provider (e.g. PayPal, M-Pesa, Stripe)
        var provider = senderNumber
        if (normalizedBody.contains("MPESA", ignoreCase = true) || senderNumber.contains("MPESA", ignoreCase = true)) {
            provider = "M-Pesa"
        } else if (normalizedBody.contains("PayPal", ignoreCase = true) || senderNumber.contains("PayPal", ignoreCase = true)) {
            provider = "PayPal"
        } else if (normalizedBody.contains("Stripe", ignoreCase = true)) {
            provider = "Stripe"
        } else if (normalizedBody.contains("Venmo", ignoreCase = true)) {
            provider = "Venmo"
        } else if (normalizedBody.contains("CashApp", ignoreCase = true) || normalizedBody.contains("Cash App", ignoreCase = true)) {
            provider = "CashApp"
        } else if (normalizedBody.contains("Zelle", ignoreCase = true)) {
            provider = "Zelle"
        } else if (provider.all { it.isDigit() || it == '+' }) {
            provider = "SMS Notification ($senderNumber)"
        }

        // 1. Extract Transaction ID
        var txnId: String? = null
        val txnPatterns = listOf(
            Pattern.compile("Ref(?:erence)?:?\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Transaction\\s*ID:?\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Txn\\s*ID:?\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Ref\\s*ID:?\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Code\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in txnPatterns) {
            val matcher = pattern.matcher(normalizedBody)
            if (matcher.find()) {
                txnId = matcher.group(1)
                break
            }
        }
        
        // 2. Extract Type (Sent vs Received)
        var type = "Received"
        if (normalizedBody.contains("sent to", ignoreCase = true) ||
            normalizedBody.contains("sent", ignoreCase = true) ||
            normalizedBody.contains("paid", ignoreCase = true) ||
            normalizedBody.contains("paid to", ignoreCase = true) ||
            normalizedBody.contains("transferred to", ignoreCase = true) ||
            normalizedBody.contains("withdrew", ignoreCase = true)
        ) {
            type = "Sent"
        }

        // 3. Extract Amount & Currency
        var amount = 0.0
        var currency = "USD"
        
        val amountPatterns = listOf(
            // Currencies represented by code or symbol followed by value, e.g., USD 100.00 or $100.00
            Pattern.compile("([A-Z\\$€£¥]{1,3})\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)"),
            // Value followed by code or symbol, e.g., 100.00 USD
            Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)\\s*([A-Z\\$€£¥]{1,3})")
        )
        
        var foundAmount = false
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(normalizedBody)
            if (matcher.find()) {
                val group1 = matcher.group(1)!!
                val group2 = matcher.group(2)!!
                
                val doubleVal1 = group1.replace(",", "").toDoubleOrNull()
                val doubleVal2 = group2.replace(",", "").toDoubleOrNull()
                
                if (doubleVal1 != null) {
                    amount = doubleVal1
                    currency = group2.trim()
                    foundAmount = true
                } else if (doubleVal2 != null) {
                    amount = doubleVal2
                    currency = group1.trim()
                    foundAmount = true
                }
                if (foundAmount) break
            }
        }
        
        if (!foundAmount) {
            // General fallback search for numbers
            val numPattern = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?)")
            val numMatcher = numPattern.matcher(normalizedBody)
            if (numMatcher.find()) {
                amount = numMatcher.group(1)!!.replace(",", "").toDoubleOrNull() ?: 0.0
            }
        }

        // 4. Extract Sender / Receiver names
        var senderName = "Unknown"
        var receiverName = "Unknown"
        
        if (type == "Sent") {
            senderName = "You"
            // Search for receiver: typically after "to"
            val receiverPatterns = listOf(
                Pattern.compile("to\\s+([^\\.\\,\\d]+?)(?:\\s+(?:on|at|Ref|Transaction|Fee|Code|date|$))", Pattern.CASE_INSENSITIVE),
                Pattern.compile("paid\\s+([^\\.\\,\\d]+?)(?:\\s+(?:on|at|Ref|Transaction|Fee|Code|date|$))", Pattern.CASE_INSENSITIVE)
            )
            for (pattern in receiverPatterns) {
                val matcher = pattern.matcher(normalizedBody)
                if (matcher.find()) {
                    val name = matcher.group(1)!!.trim()
                    if (name.isNotEmpty() && !name.contains("ref", ignoreCase = true)) {
                        receiverName = name
                        break
                    }
                }
            }
        } else {
            // Received
            receiverName = "You"
            // Search for sender: typically after "from"
            val senderPatterns = listOf(
                Pattern.compile("from\\s+([^\\.\\,\\d]+?)(?:\\s+(?:on|at|Ref|Transaction|Fee|Code|date|$))", Pattern.CASE_INSENSITIVE),
                Pattern.compile("received\\s+(?:[A-Z\\$€£¥]{1,3})\\s*[\\d\\.,]+\\s+from\\s+([^\\.\\,\\d]+?)(?:\\s+(?:on|at|Ref|Transaction|Fee|Code|date|$))", Pattern.CASE_INSENSITIVE)
            )
            for (pattern in senderPatterns) {
                val matcher = pattern.matcher(normalizedBody)
                if (matcher.find()) {
                    val name = matcher.group(matcher.groupCount())!!.trim()
                    if (name.isNotEmpty() && !name.contains("ref", ignoreCase = true)) {
                        senderName = name
                        break
                    }
                }
            }
        }

        senderName = cleanName(senderName)
        receiverName = cleanName(receiverName)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())

        return Transaction(
            amount = amount,
            currency = currency,
            sender = senderName,
            receiver = receiverName,
            provider = provider,
            transactionId = txnId,
            timestamp = timestamp,
            type = type,
            rawSms = smsBody
        )
    }

    private fun cleanName(name: String): String {
        var n = name.trim()
        val removeKeywords = listOf("Sent", "Received", "Ref", "Transaction", "Txn", "ID", "Code", "Fee", "Balance", "date", "on", "at")
        for (kw in removeKeywords) {
            if (n.endsWith(" $kw", ignoreCase = true)) {
                n = n.substring(0, n.length - kw.length).trim()
            }
        }
        n = n.removeSuffix(".")
        n = n.removeSuffix(",")
        return n.trim()
    }
}
