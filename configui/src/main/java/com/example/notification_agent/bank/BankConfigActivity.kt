@file:Suppress("UNUSED_VALUE")

package com.example.notification_agent.bank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Compose host for the multi-bank configuration screens. This is the only part
 * of the app built with Compose (the rest uses XML/ViewBinding). Optionally
 * gated behind a local 4-digit PIN stored in DataStore.
 */
class BankConfigActivity : ComponentActivity() {

    private val viewModel: BankConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BankConfigRoot(viewModel, onFinish = { finish() })
            }
        }
    }
}

private sealed interface Screen {
    data object List : Screen
    data class Editor(val config: BankConfig?) : Screen
    data object PinSettings : Screen
}

@Composable
private fun BankConfigRoot(viewModel: BankConfigViewModel, onFinish: () -> Unit) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val pinEnabled by viewModel.pinEnabled.collectAsStateWithLifecycle()
    var unlocked by remember { mutableStateOf(false) }

    if (pinEnabled && !unlocked) {
        PinGateScreen(
            onVerify = { viewModel.verifyPin(it) },
            onUnlocked = { unlocked = true }
        )
        return
    }

    var screen: Screen by remember { mutableStateOf(Screen.List) }
    when (val s = screen) {
        is Screen.List -> BankListScreen(
            viewModel = viewModel,
            onAdd = { screen = Screen.Editor(null) },
            onEdit = { screen = Screen.Editor(it) },
            onPinSettings = { screen = Screen.PinSettings },
            onBack = onFinish
        )
        is Screen.Editor -> BankEditorScreen(
            existing = s.config,
            existingConfigs = configs,
            onCancel = { screen = Screen.List },
            onSave = { config, isNew ->
                viewModel.save(config, isNew)
                screen = Screen.List
            }
        )
        is Screen.PinSettings -> PinSettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.List }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankListScreen(
    viewModel: BankConfigViewModel,
    onAdd: () -> Unit,
    onEdit: (BankConfig) -> Unit,
    onPinSettings: () -> Unit,
    onBack: () -> Unit
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, stringResource(R.string.bank_add))
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (configs.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.bank_configs_empty),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(configs, key = { it.id }) { config ->
                        BankRow(
                            config = config,
                            onToggle = { viewModel.setEnabled(config.id, it) },
                            onEdit = { onEdit(config) },
                            onDelete = { viewModel.delete(config.id) }
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.bank_app_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BankRow(
    config: BankConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(config.bankName, style = MaterialTheme.typography.titleMedium)
                Text(
                    config.endpointUrl,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
            Switch(checked = config.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, stringResource(R.string.bank_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, stringResource(R.string.bank_delete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankEditorScreen(
    existing: BankConfig?,
    existingConfigs: List<BankConfig>,
    onCancel: () -> Unit,
    onSave: (BankConfig, Boolean) -> Unit
) {
    val isNew = existing == null
    val hasSavedApiKey = !existing?.apiKey.isNullOrBlank()
    var selectedBank by remember { mutableStateOf(SupportedBank.fromCode(existing?.bankName)) }
    var bankMenuExpanded by remember { mutableStateOf(false) }
    var endpointUrl by remember { mutableStateOf(existing?.endpointUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyDirty by remember { mutableStateOf(false) }
    var clearSavedApiKey by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(existing?.isEnabled ?: true) }
    var showErrors by remember { mutableStateOf(false) }

    val nameError = selectedBank == null
    val duplicateBankError = selectedBank != null && existingConfigs.any { config ->
        config.id != existing?.id && SupportedBank.fromCode(config.bankName) == selectedBank
    }
    val urlError = !endpointUrl.startsWith("http://") && !endpointUrl.startsWith("https://")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (isNew) R.string.bank_add else R.string.bank_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.bank_cancel))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = bankMenuExpanded,
                onExpandedChange = { bankMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedBank?.code.orEmpty(),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.bank_field_name)) },
                    singleLine = true,
                    readOnly = true,
                    isError = showErrors && (nameError || duplicateBankError),
                    supportingText = {
                        if (showErrors && nameError) {
                            Text(stringResource(R.string.bank_validation_name))
                        } else if (showErrors && duplicateBankError) {
                            Text(stringResource(R.string.bank_validation_duplicate))
                        }
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                DropdownMenu(
                    expanded = bankMenuExpanded,
                    onDismissRequest = { bankMenuExpanded = false }
                ) {
                    SupportedBank.entries.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank.code) },
                            onClick = {
                                selectedBank = bank
                                bankMenuExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = endpointUrl,
                onValueChange = { endpointUrl = it },
                label = { Text(stringResource(R.string.bank_field_endpoint)) },
                singleLine = true,
                isError = showErrors && urlError,
                supportingText = {
                    if (showErrors && urlError) Text(stringResource(R.string.bank_validation_endpoint))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    apiKeyDirty = true
                    clearSavedApiKey = false
                },
                label = {
                    Text(
                        stringResource(
                            if (hasSavedApiKey) R.string.bank_field_apikey_replace
                            else R.string.bank_field_apikey
                        )
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    if (hasSavedApiKey) {
                        Text(
                            stringResource(
                                if (clearSavedApiKey) R.string.bank_apikey_clear_pending
                                else R.string.bank_apikey_hidden_help
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (hasSavedApiKey) {
                OutlinedButton(
                    onClick = {
                        apiKey = ""
                        apiKeyDirty = true
                        clearSavedApiKey = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.bank_apikey_clear))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bank_field_enabled), Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.bank_cancel))
                }
                Button(
                    onClick = {
                        if (nameError || duplicateBankError || urlError) {
                            showErrors = true
                        } else {
                            val savedApiKey = when {
                                apiKeyDirty -> apiKey.trim()
                                else -> existing?.apiKey.orEmpty()
                            }
                            val saved = (existing ?: BankConfig()).copy(
                                bankName = requireNotNull(selectedBank).code,
                                endpointUrl = endpointUrl.trim(),
                                apiKey = savedApiKey,
                                isEnabled = enabled
                            )
                            onSave(saved, isNew)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.bank_save))
                }
            }
        }
    }
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

