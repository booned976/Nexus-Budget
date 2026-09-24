package com.nexusbudget.connectors.simplefin

import com.nexusbudget.connectors.AccountTypeGuesser
import com.nexusbudget.connectors.ConnectorException
import com.nexusbudget.connectors.SyncResult
import com.nexusbudget.connectors.SyncedAccount
import com.nexusbudget.connectors.SyncedHolding
import com.nexusbudget.connectors.SyncedTransaction
import com.nexusbudget.connectors.sanitize
import com.nexusbudget.core.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64

@Serializable
data class SimpleFinOrg(
    val domain: String? = null,
    val name: String? = null,
    @SerialName("sfin-url") val sfinUrl: String? = null,
    val url: String? = null,
    val id: String? = null,
)

@Serializable
data class SimpleFinTransaction(
    val id: String,
    val posted: Long = 0,
    val amount: String,
    val description: String = "",
    val payee: String? = null,
    val memo: String? = null,
    @SerialName("transacted_at") val transactedAt: Long? = null,
    val pending: Boolean = false,
)

@Serializable
data class SimpleFinAccount(
    val org: SimpleFinOrg = SimpleFinOrg(),
    val id: String,
    val name: String,
    val currency: String = "USD",
    val balance: String,
    @SerialName("available-balance") val availableBalance: String? = null,
    @SerialName("balance-date") val balanceDate: Long = 0,
    val transactions: List<SimpleFinTransaction> = emptyList(),
    val holdings: List<JsonObject> = emptyList(),
)

@Serializable
data class SimpleFinAccountSet(
    val errors: List<String> = emptyList(),
    val errlist: List<JsonObject> = emptyList(),
    val accounts: List<SimpleFinAccount> = emptyList(),
)

/**
 * Client for the SimpleFIN protocol, a read-only standard for sharing bank data.
 *
 * The user creates a setup token with a SimpleFIN server, pastes it into the app, and the app
 * claims it once to receive an access URL (which embeds Basic Auth credentials). The access URL
 * can only read balances and transactions — it can never move money.
 */
class SimpleFinClient(
    private val http: OkHttpClient,
    private val requireHttps: Boolean = true,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Decodes a setup token into its one-time claim URL. */
    fun decodeSetupToken(setupToken: String): HttpUrl {
        val cleaned = setupToken.trim().replace(Regex("\\s"), "")
        if (cleaned.isEmpty()) throw ConnectorException.InvalidInput("Paste the setup token from your SimpleFIN account.")
        val decoded = try {
            String(Base64.getDecoder().decode(cleaned)).trim()
        } catch (e: IllegalArgumentException) {
            try {
                String(Base64.getUrlDecoder().decode(cleaned)).trim()
            } catch (e2: IllegalArgumentException) {
                throw ConnectorException.InvalidInput("That doesn't look like a SimpleFIN setup token.")
            }
        }
        val url = decoded.toHttpUrlOrNull() ?: throw ConnectorException.InvalidInput("That doesn't look like a SimpleFIN setup token.")
        if (requireHttps && !url.isHttps) throw ConnectorException.InvalidInput("SimpleFIN tokens must use a secure (https) address.")
        return url
    }

    /** Exchanges a setup token for a long-lived, read-only access URL. The token can only be claimed once. */
    suspend fun claim(setupToken: String): String = withContext(Dispatchers.IO) {
        val claimUrl = decodeSetupToken(setupToken)
        val request = Request.Builder().url(claimUrl).post(ByteArray(0).toRequestBody()).build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 403 -> throw ConnectorException.ReauthRequired(
                        "This setup token was already used or is invalid. If you didn't use it yourself, " +
                            "disable it in your SimpleFIN account because it may have been compromised, then create a new one.",
                    )
                    !response.isSuccessful -> throw ConnectorException.Provider("SimpleFIN returned an error (${response.code}).")
                }
                val accessUrl = response.body?.string()?.trim().orEmpty()
                val parsed = accessUrl.toHttpUrlOrNull() ?: throw ConnectorException.Provider("SimpleFIN returned an unexpected response.")
                if (requireHttps && !parsed.isHttps) throw ConnectorException.Provider("SimpleFIN returned an insecure address.")
                accessUrl
            }
        } catch (e: ConnectorException) {
            throw e
        } catch (e: IOException) {
            throw ConnectorException.Network("Couldn't reach SimpleFIN. Check your connection and try again.", e)
        }
    }

    suspend fun fetchAccounts(accessUrl: String, startDate: Instant?, balancesOnly: Boolean = false): SimpleFinAccountSet =
        withContext(Dispatchers.IO) {
            val base = accessUrl.toHttpUrlOrNull() ?: throw ConnectorException.ReauthRequired("The saved SimpleFIN connection is invalid. Please reconnect.")
            if (requireHttps && !base.isHttps) throw ConnectorException.ReauthRequired("The saved SimpleFIN connection is insecure. Please reconnect.")
            val url = base.newBuilder()
                .username("")
                .password("")
                .addPathSegment("accounts")
                .apply {
                    if (startDate != null) addQueryParameter("start-date", startDate.epochSecond.toString())
                    if (balancesOnly) addQueryParameter("balances-only", "1") else addQueryParameter("pending", "1")
                }
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(base.username, base.password))
                .get()
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        402 -> throw ConnectorException.PaymentRequired("Your SimpleFIN subscription needs attention. Check your SimpleFIN account.")
                        403 -> throw ConnectorException.ReauthRequired("SimpleFIN access was revoked or expired. Create a new setup token to reconnect.")
                        429 -> throw ConnectorException.RateLimited("SimpleFIN is limiting requests right now. Try again later.")
                    }
                    if (!response.isSuccessful) throw ConnectorException.Provider("SimpleFIN returned an error (${response.code}).")
                    json.decodeFromString(SimpleFinAccountSet.serializer(), response.body?.string().orEmpty())
                }
            } catch (e: ConnectorException) {
                throw e
            } catch (e: kotlinx.serialization.SerializationException) {
                throw ConnectorException.Provider("SimpleFIN returned data the app couldn't read.")
            } catch (e: IOException) {
                throw ConnectorException.Network("Couldn't reach SimpleFIN. Check your connection and try again.", e)
            }
        }

    /** Fetches and converts accounts and transactions since [startDate]. */
    suspend fun sync(accessUrl: String, startDate: Instant): SyncResult = toSyncResult(fetchAccounts(accessUrl, startDate), startDate)

    fun toSyncResult(set: SimpleFinAccountSet, startDate: Instant?): SyncResult {
        val accounts = set.accounts.map { account ->
            val raw = Money.parse(account.balance) ?: 0
            val type = AccountTypeGuesser.guess(account.name, raw, account.holdings.isNotEmpty())
            // SimpleFIN reports debts as negative balances; the app stores the amount owed as positive.
            val balance = if (type.isLiability) -raw else raw
            val available = account.availableBalance?.let(Money::parse)?.let { if (type.isLiability) null else it }
            SyncedAccount(
                externalId = account.id,
                name = sanitize(account.name, 80),
                type = type,
                balance = balance,
                available = available,
                currency = account.currency.takeIf { it.length == 3 } ?: "USD",
                institution = (account.org.name ?: account.org.domain)?.let { sanitize(it, 80) },
            )
        }
        val transactions = set.accounts.flatMap { account ->
            account.transactions.mapNotNull { txn ->
                val amount = Money.parse(txn.amount) ?: return@mapNotNull null
                val epoch = txn.transactedAt?.takeIf { it > 0 } ?: txn.posted.takeIf { it > 0 } ?: Instant.now().epochSecond
                val description = sanitize(listOfNotNull(txn.payee, txn.description).firstOrNull { it.isNotBlank() } ?: txn.description, 200)
                SyncedTransaction(
                    externalId = "${account.id}/${txn.id}",
                    accountExternalId = account.id,
                    date = LocalDate.ofInstant(Instant.ofEpochSecond(epoch), zone),
                    amount = amount,
                    description = description.ifBlank { "Transaction" },
                    pending = txn.pending || txn.posted == 0L,
                )
            }
        }
        val holdings = set.accounts.flatMap { account ->
            account.holdings.mapNotNull { holding -> parseHolding(account.id, holding) }
        }
        val warnings = (set.errors + set.errlist.mapNotNull { (it["msg"] as? JsonPrimitive)?.contentOrNull })
            .map { sanitize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        return SyncResult(
            accounts = accounts,
            transactions = transactions,
            holdings = holdings,
            replacesPendingSince = startDate?.let { LocalDate.ofInstant(it, zone) },
            warnings = warnings,
        )
    }

    private fun parseHolding(accountId: String, holding: JsonObject): SyncedHolding? {
        fun str(key: String): String? = (holding[key] as? JsonPrimitive)?.contentOrNull
        val id = str("id") ?: return null
        val value = str("market_value")?.let(Money::parse) ?: return null
        return SyncedHolding(
            accountExternalId = accountId,
            securityId = id,
            name = sanitize(str("description") ?: str("symbol") ?: "Holding", 80),
            ticker = str("symbol")?.let { sanitize(it, 12) },
            quantity = str("shares")?.toDoubleOrNull() ?: 0.0,
            value = value,
            costBasis = str("cost_basis")?.let(Money::parse),
        )
    }
}
