package com.example.notification_agent.bank

import java.util.UUID

/**
 * A single bank forwarding configuration. Each device stores N of these and
 * forwards matching payment notifications to [endpointUrl].
 *
 * Persisted via [BankConfigRepository] (DataStore) so it survives reboots and
 * app upgrades.
 */
data class BankConfig(
    /** Stable unique id. Generated once on creation. */
    val id: String = UUID.randomUUID().toString(),
    /** Display / source bank name, e.g. "KTB", "SCB". */
    val bankName: String = "",
    /** Full webhook endpoint URL (may contain a `{bankAccountId}` segment). */
    val endpointUrl: String = "",
    /** Optional custom token sent as the `API_KEY` header when non-blank. */
    val apiKey: String = "",
    /** When false the config is kept but skipped by the dispatcher. */
    val isEnabled: Boolean = true
)
