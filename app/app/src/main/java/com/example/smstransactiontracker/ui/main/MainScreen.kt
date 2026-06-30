package com.example.smstransactiontracker.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import com.example.smstransactiontracker.SMSReceiver
import com.example.smstransactiontracker.Transaction
import com.example.smstransactiontracker.TransactionHistory
import com.example.smstransactiontracker.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val sharedPrefs = remember { context.getSharedPreferences("sms_tracker_prefs", Context.MODE_PRIVATE) }
  
  var backendUrl by remember {
    mutableStateOf(sharedPrefs.getString("backend_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000")
  }
  
  var hasSmsPermissions by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    )
  }
  
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    hasSmsPermissions = permissions[Manifest.permission.RECEIVE_SMS] == true &&
                         permissions[Manifest.permission.READ_SMS] == true
  }

  var simulationSmsText by remember {
    mutableStateOf("Received USD 120.50 from Jane Doe. Ref: TXN987654321")
  }
  var simulationSender by remember {
    mutableStateOf("+15551234567")
  }

  val transactions by TransactionHistory.transactions.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("SMS Transaction Tracker", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(16.dp)
        .fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      
      // 1. Sync Settings Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Sync Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          
          OutlinedTextField(
            value = backendUrl,
            onValueChange = {
              backendUrl = it
              sharedPrefs.edit().putString("backend_url", it).apply()
            },
            label = { Text("Backend Server URL") },
            placeholder = { Text("http://192.168.x.x:8000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(
                imageVector = if (hasSmsPermissions) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (hasSmsPermissions) Color(0xFF4CAF50) else Color(0xFFF44336)
              )
              Text(
                text = if (hasSmsPermissions) "SMS Listener Active" else "SMS Permissions Missing",
                fontWeight = FontWeight.Medium,
                color = if (hasSmsPermissions) Color(0xFF2E7D32) else Color(0xFFC62828)
              )
            }
            
            if (!hasSmsPermissions) {
              Button(
                onClick = {
                  permissionLauncher.launch(
                    arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                  )
                }
              ) {
                Text("Grant Permissions")
              }
            }
          }
        }
      }

      // 2. Sandbox Simulator Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("SMS Simulator Sandbox", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          
          OutlinedTextField(
            value = simulationSender,
            onValueChange = { simulationSender = it },
            label = { Text("Sender Number / ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )

          OutlinedTextField(
            value = simulationSmsText,
            onValueChange = { simulationSmsText = it },
            label = { Text("Raw SMS Message Body") },
            modifier = Modifier.fillMaxWidth()
          )

          Button(
            onClick = {
              val t = TransactionParser.parse(simulationSmsText, simulationSender)
              if (t != null) {
                TransactionHistory.addTransaction(t)
                CoroutineScope(Dispatchers.IO).launch {
                  try {
                    SMSReceiver.sendToServer(context, t)
                    launch(Dispatchers.Main) {
                      Toast.makeText(context, "Simulated SMS sent to server!", Toast.LENGTH_SHORT).show()
                    }
                  } catch (e: Exception) {
                    Log.e("MainScreen", "Error", e)
                    launch(Dispatchers.Main) {
                      Toast.makeText(context, "Parsed, but failed to send to server: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                  }
                }
              } else {
                Toast.makeText(context, "Failed to parse transaction from text", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.align(Alignment.End)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simulate SMS & Sync")
          }
        }
      }

      // 3. Intercepted Transactions Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Recent Transactions (${transactions.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (transactions.isNotEmpty()) {
          TextButton(onClick = { TransactionHistory.clear() }) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear List")
          }
        }
      }

      // 4. Transactions List
      if (transactions.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
          contentAlignment = Alignment.Center
        ) {
          Text("No transactions captured yet.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(transactions) { txn ->
            TransactionItem(txn)
          }
        }
      }
    }
  }
}

@Composable
fun TransactionItem(txn: Transaction) {
  val isReceived = txn.type.equals("Received", ignoreCase = true)
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Sent vs Received badge
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (isReceived) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = txn.type.uppercase(),
              color = if (isReceived) Color(0xFF2E7D32) else Color(0xFFC62828),
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            )
          }
          
          Text(txn.provider, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        
        Text(
          text = "${if (isReceived) "+" else "-"} ${txn.currency} ${String.format("%.2f", txn.amount)}",
          color = if (isReceived) Color(0xFF2E7D32) else Color(0xFFC62828),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.bodyLarge
        )
      }
      
      Spacer(modifier = Modifier.height(8.dp))
      
      Text(
        text = if (isReceived) "From: ${txn.sender}" else "To: ${txn.receiver}",
        style = MaterialTheme.typography.bodyMedium
      )
      
      if (!txn.transactionId.isNullOrEmpty()) {
        Text(
          text = "ID: ${txn.transactionId}",
          style = MaterialTheme.typography.bodySmall,
          color = Color.Gray
        )
      }
      
      Text(
        text = txn.timestamp,
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        modifier = Modifier.align(Alignment.End)
      )
    }
  }
}
