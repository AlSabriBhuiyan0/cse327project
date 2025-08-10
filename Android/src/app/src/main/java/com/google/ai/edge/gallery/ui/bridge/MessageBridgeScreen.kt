package com.google.ai.edge.gallery.ui.bridge

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.bridge.*
import com.google.ai.edge.gallery.bridge.gmail.GmailConnectionState
import com.google.ai.edge.gallery.bridge.permission.BridgePermissionHelper
import com.google.ai.edge.gallery.bridge.permission.BridgePermissionsHandler
import com.google.ai.edge.gallery.bridge.telegram.TelegramConnectionState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main screen for the Telegram-Gmail Bridge feature
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBridgeScreen(
    viewModel: MessageBridgeViewModel = hiltViewModel(),
    navigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val telegramConnected by viewModel.telegramConnected.collectAsState()
    val gmailConnected by viewModel.gmailConnected.collectAsState()
    val bridgeRules by viewModel.bridgeRules.collectAsState()
    val recentMessages by viewModel.recentMessages.collectAsState()

    var showTelegramAuthDialog by remember { mutableStateOf(false) }
    var showGmailAuthDialog by remember { mutableStateOf(false) }
    var showAddRuleDialog by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    viewModel.initializeGmail(account)
                }
            } catch (e: ApiException) {
                Log.e("MessageBridgeScreen", "Google sign in failed", e)
            }
        }
    }

    // Initialize bridge service if needed when screen opens
    LaunchedEffect(Unit) {
        viewModel.initializeBridgeServiceIfNeeded()
    }
    
    // Permission handling
    val activity = LocalContext.current as? FragmentActivity
    if (activity != null) {
        val permissionHelper = remember { BridgePermissionHelper(activity) }
        
        BridgePermissionsHandler(
            context = context,
            permissionHelper = permissionHelper,
            onPermissionsGranted = {
                // Permissions granted, proceed with full functionality
                Log.d("MessageBridgeScreen", "All permissions granted")
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Bridge") },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddRuleDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rule")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Cards
            item {
                Text(
                    "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Telegram Connection Card
                    ConnectionCard(
                        title = "Telegram",
                        connected = telegramConnected,
                        icon = Icons.Default.Send,
                        modifier = Modifier.weight(1f),
                        onConnect = { showTelegramAuthDialog = true },
                        onDisconnect = { viewModel.disconnectTelegram() }
                    )

                    // Gmail Connection Card
                    ConnectionCard(
                        title = "Gmail",
                        connected = gmailConnected,
                        icon = Icons.Default.Email,
                        modifier = Modifier.weight(1f),
                        onConnect = {
                            val signInIntent = GoogleSignIn.getClient(
                                context,
                                viewModel.getGoogleSignInOptions()
                            ).signInIntent
                            googleSignInLauncher.launch(signInIntent)
                        },
                        onDisconnect = { viewModel.disconnectGmail() }
                    )
                }
            }

            // Bridge Rules
            item {
                Text(
                    "Bridge Rules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (bridgeRules.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Bridge Rules",
                        description = "Add a rule to start forwarding messages between platforms",
                        icon = Icons.Default.SwapHoriz,
                        actionText = "Add Rule",
                        onAction = { showAddRuleDialog = true }
                    )
                }
            } else {
                items(bridgeRules) { rule ->
                    BridgeRuleCard(
                        rule = rule,
                        onRemove = { viewModel.removeBridgeRule(rule.id) }
                    )
                }
            }

            // Recent Messages
            item {
                Text(
                    "Recent Messages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (recentMessages.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Recent Messages",
                        description = "Messages that pass through the bridge will appear here",
                        icon = Icons.Default.Message,
                        actionText = null,
                        onAction = null
                    )
                }
            } else {
                items(recentMessages) { message ->
                    BridgeMessageCard(message = message)
                }

                item {
                    Button(
                        onClick = { viewModel.clearRecentMessages() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Message History")
                    }
                }
            }
        }
    }

    // Telegram Authentication Dialog
    if (showTelegramAuthDialog) {
        TelegramAuthDialog(
            connectionState = viewModel.telegramConnectionState.collectAsState().value,
            onDismiss = { showTelegramAuthDialog = false },
            onInitialize = { apiId, apiHash -> viewModel.initializeTelegram(apiId, apiHash) },
            onPhoneSubmit = { phoneNumber -> viewModel.startTelegramAuth(phoneNumber) },
            onCodeSubmit = { code -> viewModel.submitTelegramCode(code) },
            onPasswordSubmit = { password -> viewModel.submitTelegramPassword(password) }
        )
    }

    // Add Bridge Rule Dialog
    if (showAddRuleDialog) {
        AddBridgeRuleDialog(
            telegramConnected = telegramConnected,
            gmailConnected = gmailConnected,
            telegramChats = viewModel.telegramChats.collectAsState().value,
            gmailLabels = viewModel.gmailLabels.collectAsState().value,
            onDismiss = { showAddRuleDialog = false },
            onAddRule = { rule ->
                val added = viewModel.addBridgeRule(rule)
                if (added) {
                    showAddRuleDialog = false
                }
            }
        )
    }
}

@Composable
fun ConnectionCard(
    title: String,
    connected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (connected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (connected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (connected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (connected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.bodyMedium,
                color = if (connected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = if (connected) onDisconnect else onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connected)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (connected) "Disconnect" else "Connect")
            }
        }
    }
}

@Composable
fun BridgeRuleCard(
    rule: BridgeRule,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source platform
                SourceDestinationLabel(
                    platform = rule.sourceType,
                    identifier = rule.sourceIdentifier,
                    isSource = true,
                    modifier = Modifier.weight(1f)
                )

                // Arrow
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "to",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Target platform
                SourceDestinationLabel(
                    platform = rule.targetType,
                    identifier = rule.targetIdentifier,
                    isSource = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { /* TODO: Implement enable/disable */ }
                )

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Rule",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun SourceDestinationLabel(
    platform: MessagePlatform,
    identifier: String,
    isSource: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isSource) Arrangement.Start else Arrangement.End
    ) {
        if (!isSource) {
            Text(
                text = identifier,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            imageVector = when (platform) {
                MessagePlatform.TELEGRAM -> Icons.Default.Send
                MessagePlatform.GMAIL -> Icons.Default.Email
            },
            contentDescription = null,
            tint = when (platform) {
                MessagePlatform.TELEGRAM -> MaterialTheme.colorScheme.primary
                MessagePlatform.GMAIL -> MaterialTheme.colorScheme.secondary
            }
        )

        if (isSource) {
            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = identifier,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BridgeMessageCard(message: BridgeMessage) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (message.sourceType) {
                MessagePlatform.TELEGRAM -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                MessagePlatform.GMAIL -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (message.sourceType) {
                            MessagePlatform.TELEGRAM -> Icons.Default.Send
                            MessagePlatform.GMAIL -> Icons.Default.Email
                        },
                        contentDescription = null,
                        tint = when (message.sourceType) {
                            MessagePlatform.TELEGRAM -> MaterialTheme.colorScheme.primary
                            MessagePlatform.GMAIL -> MaterialTheme.colorScheme.secondary
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = when (message.sourceType) {
                            MessagePlatform.TELEGRAM -> "Telegram"
                            MessagePlatform.GMAIL -> "Gmail"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Timestamp
                Text(
                    text = dateFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message content
            when (message) {
                is BridgeMessage.TelegramBridgeMessage -> {
                    Text(
                        text = "${message.message.senderName}: ${message.message.text}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is BridgeMessage.GmailBridgeMessage -> {
                    Text(
                        text = "Subject: ${message.message.subject}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = message.message.body,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3
                    )
                }
            }

            if (message.forwarded) {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Forwarded",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionText: String?,
    onAction: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onAction) {
                    Text(actionText)
                }
            }
        }
    }
}
