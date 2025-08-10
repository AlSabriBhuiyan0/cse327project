package com.google.ai.edge.gallery.ui.bridge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.ai.edge.gallery.bridge.BridgeRule
import com.google.ai.edge.gallery.bridge.MessagePlatform
import com.google.ai.edge.gallery.bridge.gmail.GmailLabel
import com.google.ai.edge.gallery.bridge.telegram.TelegramChat
import com.google.ai.edge.gallery.bridge.telegram.TelegramConnectionState

/**
 * Dialog for Telegram authentication
 */
@Composable
fun TelegramAuthDialog(
    connectionState: TelegramConnectionState,
    onDismiss: () -> Unit,
    onInitialize: (apiId: Int, apiHash: String) -> Unit,
    onPhoneSubmit: (phoneNumber: String) -> Unit,
    onCodeSubmit: (code: String) -> Unit,
    onPasswordSubmit: (password: String) -> Unit
) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connect to Telegram",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (connectionState) {
                    TelegramConnectionState.DISCONNECTED,
                    TelegramConnectionState.CONNECTING,
                    TelegramConnectionState.ERROR -> {
                        // API credentials entry
                        OutlinedTextField(
                            value = apiId,
                            onValueChange = { apiId = it },
                            label = { Text("API ID") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = apiHash,
                            onValueChange = { apiHash = it },
                            label = { Text("API Hash") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                onInitialize(apiId.toIntOrNull() ?: 0, apiHash)
                            },
                            enabled = apiId.isNotEmpty() && apiHash.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Initialize")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You need to create a Telegram API app to get these credentials. Visit https://my.telegram.org/apps",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TelegramConnectionState.WAITING_FOR_PHONE -> {
                        // Phone number entry
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone Number (with country code)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onPhoneSubmit(phoneNumber) },
                            enabled = phoneNumber.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Phone Number")
                        }
                    }

                    TelegramConnectionState.WAITING_FOR_CODE -> {
                        // Verification code entry
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it },
                            label = { Text("Verification Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onCodeSubmit(verificationCode) },
                            enabled = verificationCode.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Code")
                        }
                    }

                    TelegramConnectionState.WAITING_FOR_PASSWORD -> {
                        // 2FA password entry
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Two-Factor Authentication Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onPasswordSubmit(password) },
                            enabled = password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Password")
                        }
                    }

                    else -> {
                        CircularProgressIndicator()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Dialog for adding a new bridge rule
 */
@Composable
fun AddBridgeRuleDialog(
    telegramConnected: Boolean,
    gmailConnected: Boolean,
    telegramChats: List<TelegramChat>,
    gmailLabels: List<GmailLabel>,
    onDismiss: () -> Unit,
    onAddRule: (BridgeRule) -> Unit
) {
    var selectedSourceType by remember { mutableStateOf(MessagePlatform.TELEGRAM) }
    var selectedTargetType by remember { mutableStateOf(MessagePlatform.GMAIL) }
    var selectedTelegramChatId by remember { mutableStateOf("") }
    var selectedGmailLabel by remember { mutableStateOf("") }
    var targetEmail by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Add Bridge Rule",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Source platform selection
                Text(
                    text = "Source Platform",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformSelectionButton(
                        platform = MessagePlatform.TELEGRAM,
                        isSelected = selectedSourceType == MessagePlatform.TELEGRAM,
                        enabled = telegramConnected,
                        onClick = { selectedSourceType = MessagePlatform.TELEGRAM }
                    )

                    PlatformSelectionButton(
                        platform = MessagePlatform.GMAIL,
                        isSelected = selectedSourceType == MessagePlatform.GMAIL,
                        enabled = gmailConnected,
                        onClick = { selectedSourceType = MessagePlatform.GMAIL }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Source identifier selection
                Text(
                    text = "Source ${if (selectedSourceType == MessagePlatform.TELEGRAM) "Chat" else "Label"}",
                    style = MaterialTheme.typography.titleMedium
                )

                when (selectedSourceType) {
                    MessagePlatform.TELEGRAM -> {
                        if (telegramChats.isEmpty()) {
                            Text(
                                text = "No Telegram chats available",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            TelegramChatDropdown(
                                chats = telegramChats,
                                selectedChatId = selectedTelegramChatId,
                                onChatSelected = { selectedTelegramChatId = it }
                            )
                        }
                    }
                    MessagePlatform.GMAIL -> {
                        if (gmailLabels.isEmpty()) {
                            Text(
                                text = "No Gmail labels available",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            GmailLabelDropdown(
                                labels = gmailLabels,
                                selectedLabelId = selectedGmailLabel,
                                onLabelSelected = { selectedGmailLabel = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Target platform selection
                Text(
                    text = "Target Platform",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlatformSelectionButton(
                        platform = MessagePlatform.TELEGRAM,
                        isSelected = selectedTargetType == MessagePlatform.TELEGRAM,
                        enabled = telegramConnected && selectedSourceType != MessagePlatform.TELEGRAM,
                        onClick = { selectedTargetType = MessagePlatform.TELEGRAM }
                    )

                    PlatformSelectionButton(
                        platform = MessagePlatform.GMAIL,
                        isSelected = selectedTargetType == MessagePlatform.GMAIL,
                        enabled = gmailConnected && selectedSourceType != MessagePlatform.GMAIL,
                        onClick = { selectedTargetType = MessagePlatform.GMAIL }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Target identifier selection
                Text(
                    text = "Target ${if (selectedTargetType == MessagePlatform.TELEGRAM) "Chat" else "Email"}",
                    style = MaterialTheme.typography.titleMedium
                )

                when (selectedTargetType) {
                    MessagePlatform.TELEGRAM -> {
                        if (telegramChats.isEmpty()) {
                            Text(
                                text = "No Telegram chats available",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            TelegramChatDropdown(
                                chats = telegramChats,
                                selectedChatId = selectedTelegramChatId,
                                onChatSelected = { selectedTelegramChatId = it }
                            )
                        }
                    }
                    MessagePlatform.GMAIL -> {
                        OutlinedTextField(
                            value = targetEmail,
                            onValueChange = { targetEmail = it },
                            label = { Text("Target Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val rule = BridgeRule(
                                sourceType = selectedSourceType,
                                targetType = selectedTargetType,
                                sourceIdentifier = when (selectedSourceType) {
                                    MessagePlatform.TELEGRAM -> selectedTelegramChatId
                                    MessagePlatform.GMAIL -> selectedGmailLabel
                                },
                                targetIdentifier = when (selectedTargetType) {
                                    MessagePlatform.TELEGRAM -> selectedTelegramChatId
                                    MessagePlatform.GMAIL -> targetEmail
                                }
                            )
                            onAddRule(rule)
                        },
                        enabled = when {
                            selectedSourceType == MessagePlatform.TELEGRAM && selectedTelegramChatId.isEmpty() -> false
                            selectedSourceType == MessagePlatform.GMAIL && selectedGmailLabel.isEmpty() -> false
                            selectedTargetType == MessagePlatform.TELEGRAM && selectedTelegramChatId.isEmpty() -> false
                            selectedTargetType == MessagePlatform.GMAIL && targetEmail.isEmpty() -> false
                            else -> true
                        }
                    ) {
                        Text("Add Rule")
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformSelectionButton(
    platform: MessagePlatform,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = when (platform) {
                MessagePlatform.TELEGRAM -> "Telegram"
                MessagePlatform.GMAIL -> "Gmail"
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChatDropdown(
    chats: List<TelegramChat>,
    selectedChatId: String,
    onChatSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedChat = chats.find { it.id == selectedChatId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedChat?.title ?: "Select a chat",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            chats.forEach { chat ->
                DropdownMenuItem(
                    text = { Text(chat.title) },
                    onClick = {
                        onChatSelected(chat.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmailLabelDropdown(
    labels: List<GmailLabel>,
    selectedLabelId: String,
    onLabelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = labels.find { it.id == selectedLabelId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel?.name ?: "Select a label",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            labels.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label.name) },
                    onClick = {
                        onLabelSelected(label.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
