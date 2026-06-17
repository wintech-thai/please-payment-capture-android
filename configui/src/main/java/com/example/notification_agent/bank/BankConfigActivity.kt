package com.example.notification_agent.bank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class BankConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                BankConfigRoot(viewModel(), onFinish = { finish() })
            }
        }
    }
}

private sealed class Screen {
    object Settings : Screen()
    object PinSettings : Screen()
}

@Composable
private fun BankConfigRoot(viewModel: BankConfigViewModel, onFinish: () -> Unit) {
    val pinEnabled by viewModel.pinEnabled.collectAsStateWithLifecycle()
    var unlocked by remember { mutableStateOf(false) }

    if (pinEnabled && !unlocked) {
        PinGateScreen(
            onVerify = { viewModel.verifyPin(it) },
            onUnlocked = { unlocked = true }
        )
        return
    }

    var screen: Screen by remember { mutableStateOf(Screen.Settings) }
    when (screen) {
        is Screen.Settings -> BankSettingsScreen(
            viewModel = viewModel,
            onPinSettings = { screen = Screen.PinSettings },
            onBack = onFinish
        )
        is Screen.PinSettings -> PinSettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Settings }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankSettingsScreen(
    viewModel: BankConfigViewModel,
    onPinSettings: () -> Unit,
    onBack: () -> Unit
) {
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    var endpointUrl by remember(globalConfig) { mutableStateOf(globalConfig.endpointUrl) }
    var apiKey by remember(globalConfig) { mutableStateOf(globalConfig.apiKey) }
    var agentId by remember(globalConfig) { mutableStateOf(globalConfig.agentId) }
    var enabledBanks by remember(globalConfig) { mutableStateOf(globalConfig.enabledBanks) }
    var enabledLineBanks by remember(globalConfig) { mutableStateOf(globalConfig.enabledLineBanks) }
    var enabledSmsBanks by remember(globalConfig) { mutableStateOf(globalConfig.enabledSmsBanks) }
    var forwardLineBanks by remember(globalConfig) { mutableStateOf(globalConfig.forwardLineBanks) }
    var forwardSmsBanks by remember(globalConfig) { mutableStateOf(globalConfig.forwardSmsBanks) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Logic to extract Agent ID from URL
    fun extractAgentId(url: String): String {
        return try {
            val parts = url.trim().split("/")
            val last = parts.last()
            if (last.length >= 36) last else ""
        } catch (e: Exception) {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_bank_configs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.bank_back))
                    }
                },
                actions = {
                    IconButton(onClick = onPinSettings) {
                        Icon(Icons.Filled.Lock, stringResource(R.string.pin_section))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.bank_global_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = endpointUrl,
                onValueChange = { 
                    endpointUrl = it
                    val extracted = extractAgentId(it)
                    if (extracted.isNotEmpty()) {
                        agentId = extracted
                    }
                },
                label = { Text(stringResource(R.string.bank_global_endpoint)) },
                placeholder = { Text("https://api.example.com/notify/uuid") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.bank_global_apikey)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.bank_global_agentid),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = agentId.ifBlank { "None (Paste URL to extract)" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.bank_enabled_banks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            SupportedBank.entries.forEach { bank ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = bank.code, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = enabledBanks.contains(bank.code),
                            onCheckedChange = { checked ->
                                enabledBanks = if (checked) {
                                    enabledBanks + bank.code
                                } else {
                                    enabledBanks - bank.code
                                }
                            }
                        )
                    }

                    if (enabledBanks.contains(bank.code)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (bank.supportsLine) {
                                ChannelOption(
                                    label = "LINE",
                                    isEnabled = enabledLineBanks.contains(bank.code),
                                    onEnabledChange = { checked ->
                                        enabledLineBanks = if (checked) enabledLineBanks + bank.code else enabledLineBanks - bank.code
                                    },
                                    isForwarded = forwardLineBanks.contains(bank.code),
                                    onForwardedChange = { checked ->
                                        forwardLineBanks = if (checked) forwardLineBanks + bank.code else forwardLineBanks - bank.code
                                    }
                                )
                            }
                            if (bank.supportsSms) {
                                ChannelOption(
                                    label = "SMS",
                                    isEnabled = enabledSmsBanks.contains(bank.code),
                                    onEnabledChange = { checked ->
                                        enabledSmsBanks = if (checked) enabledSmsBanks + bank.code else enabledSmsBanks - bank.code
                                    },
                                    isForwarded = forwardSmsBanks.contains(bank.code),
                                    onForwardedChange = { checked ->
                                        forwardSmsBanks = if (checked) forwardSmsBanks + bank.code else forwardSmsBanks - bank.code
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    try {
                        viewModel.updateGlobal(
                            BankGlobalConfig(
                                endpointUrl = endpointUrl.trim(),
                                apiKey = apiKey.trim(),
                                agentId = agentId.trim(),
                                enabledBanks = enabledBanks,
                                enabledLineBanks = enabledLineBanks,
                                enabledSmsBanks = enabledSmsBanks,
                                forwardLineBanks = forwardLineBanks,
                                forwardSmsBanks = forwardSmsBanks
                            )
                        )
                        saveError = null
                        showSaveDialog = true
                    } catch (e: Exception) {
                        saveError = e.message
                        showSaveDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.bank_save))
            }

            Text(
                text = stringResource(R.string.bank_app_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { 
                Text(stringResource(if (saveError == null) R.string.dialog_success else R.string.dialog_error)) 
            },
            text = { 
                Text(saveError ?: stringResource(R.string.save_success)) 
            },
            confirmButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            }
        )
    }
}

@Composable
private fun ChannelOption(
    label: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    isForwarded: Boolean,
    onForwardedChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        if (isEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = "Forward to Webhook",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Switch(
                    checked = isForwarded,
                    onCheckedChange = onForwardedChange,
                    scale = 0.8f
                )
            }
        }
    }
}

@Composable
private fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.then(Modifier.scale(scale))
    )
}

@Composable
private fun PinGateScreen(
    onVerify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.pin_enter_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(stringResource(R.string.pin_enter_hint))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4 && it.all(Char::isDigit)) {
                        pin = it
                        wrong = false
                    }
                },
                singleLine = true,
                isError = wrong,
                supportingText = { if (wrong) Text(stringResource(R.string.pin_wrong)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Button(
                onClick = {
                    scope.launch {
                        if (onVerify(pin)) onUnlocked() else {
                            wrong = true
                            pin = ""
                        }
                    }
                },
                enabled = pin.length == 4
            ) {
                Text(stringResource(R.string.pin_unlock))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSettingsScreen(
    viewModel: BankConfigViewModel,
    onBack: () -> Unit
) {
    val pinEnabled by viewModel.pinEnabled.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pin_section)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.bank_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(
                    if (pinEnabled) R.string.pin_enabled_label else R.string.pin_disabled_label
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.pin_set_title))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4 && it.all(Char::isDigit)) {
                        pin = it
                        error = false
                    }
                },
                singleLine = true,
                isError = error,
                supportingText = { if (error) Text(stringResource(R.string.pin_invalid)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (pin.length == 4) {
                        viewModel.setPin(pin)
                        pin = ""
                        onBack()
                    } else {
                        error = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pin_set))
            }
            if (pinEnabled) {
                OutlinedButton(
                    onClick = {
                        viewModel.clearPin()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pin_disable))
                }
            }
        }
    }
}
