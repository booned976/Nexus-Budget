package com.nexusbudget.connectors

import com.nexusbudget.core.model.AccountType
import java.io.IOException
import java.time.LocalDate

/** Provider-neutral account data returned by a sync. Balances follow the core convention (debts positive). */
data class SyncedAccount(
    val externalId: String,
    val name: String,
    val type: AccountType,
    val balance: Long,
    val available: Long? = null,
    val creditLimit: Long? = null,
    val currency: String = "USD",
    val mask: String? = null,
    val institution: String? = null,
    val apr: Double? = null,
    val minimumPayment: Long? = null,
    val paymentDueDay: Int? = null,
)

data class SyncedTransaction(
    /** Unique within the connection. */
    val externalId: String,
    val accountExternalId: String,
    val date: LocalDate,
    /** Negative for money leaving the account. */
    val amount: Long,
    val description: String,
    val merchant: String? = null,
    val pending: Boolean = false,
    /** Suggested core category id, when the provider categorizes transactions. */
    val categoryHint: String? = null,
)

data class SyncedHolding(
    val accountExternalId: String,
    val securityId: String,
    val name: String,
    val ticker: String?,
    val quantity: Double,
    val value: Long,
    val costBasis: Long?,
)

data class SyncResult(
    val accounts: List<SyncedAccount>,
    /** New or changed transactions. */
    val transactions: List<SyncedTransaction>,
    val removedTransactionIds: List<String> = emptyList(),
    val holdings: List<SyncedHolding> = emptyList(),
    /** Opaque provider cursor to store for the next incremental sync. */
    val cursor: String? = null,
    /** Pending transactions in this window that aren't returned anymore should be deleted. */
    val replacesPendingSince: LocalDate? = null,
    /** Messages from the provider to show the user (already sanitized). */
    val warnings: List<String> = emptyList(),
)

sealed class ConnectorException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** The user needs to reconnect (credentials revoked or expired). */
    class ReauthRequired(message: String) : ConnectorException(message)

    /** The provider account needs payment (SimpleFIN Bridge subscription lapsed). */
    class PaymentRequired(message: String) : ConnectorException(message)

    class RateLimited(message: String) : ConnectorException(message)

    class InvalidInput(message: String) : ConnectorException(message)

    class Provider(message: String, val code: String? = null) : ConnectorException(message)

    class Network(message: String, cause: Throwable?) : ConnectorException(message, cause)
}

/** Strips markup and control characters from provider-supplied text before it's shown to the user. */
internal fun sanitize(text: String, maxLength: Int = 300): String =
    text.replace(Regex("<[^>]*>"), "")
        .replace(Regex("[\\p{Cntrl}&&[^\\n]]"), "")
        .trim()
        .take(maxLength)
