package com.nexusbudget.connectors.plaid

import com.nexusbudget.connectors.ConnectorException
import com.nexusbudget.connectors.SyncResult
import com.nexusbudget.connectors.SyncedAccount
import com.nexusbudget.connectors.SyncedHolding
import com.nexusbudget.connectors.SyncedTransaction
import com.nexusbudget.connectors.sanitize
import com.nexusbudget.core.model.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class PlaidEnvironment(val label: String, val baseUrl: String) {
    SANDBOX("Sandbox (test data)", "https://sandbox.plaid.com"),
    PRODUCTION("Production", "https://production.plaid.com"),
}

/** Your own Plaid API keys. They stay encrypted on this device and are sent only to Plaid. */
data class PlaidCredentials(
    val clientId: String,
    val secret: String,
    val environment: PlaidEnvironment,
)

/**
 * What the user is connecting. Only read-only Plaid products are ever requested:
 * transactions, liabilities and investments. The app never asks for payment or transfer access.
 */
enum class PlaidLinkPurpose(val products: List<String>, val optionalProducts: List<String>) {
    BANKING(listOf("transactions"), listOf("liabilities")),
    INVESTMENTS(listOf("investments"), emptyList()),
    LOANS(listOf("liabilities"), emptyList()),
    ;

    val allProducts: Set<String> get() = (products + optionalProducts).toSet()
}

data class PlaidLinkToken(val linkToken: String, val hostedLinkUrl: String?, val expiration: String?)

data class PlaidLinkedItem(val publicToken: String, val institutionName: String?, val institutionId: String?)

data class PlaidItemAccess(val accessToken: String, val itemId: String)

/**
 * Minimal Plaid client for a single user's own Plaid developer account. Linking uses Plaid's
 * Hosted Link page, opened in a browser tab, so no proprietary SDK is needed.
 */
class PlaidClient(
    private val http: OkHttpClient,
    private val credentials: PlaidCredentials,
    private val baseUrl: String = credentials.environment.baseUrl,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }
    private val mediaType = "application/json".toMediaType()

    suspend fun createLinkToken(
        userId: String,
        purpose: PlaidLinkPurpose,
        completionRedirectUri: String?,
        accessTokenForUpdate: String? = null,
    ): PlaidLinkToken {
        val update = accessTokenForUpdate != null
        val request = LinkTokenCreateRequest(
            clientName = "Nexus Budget",
            user = LinkTokenUser(userId),
            products = if (update) null else purpose.products,
            optionalProducts = if (update || purpose.optionalProducts.isEmpty()) null else purpose.optionalProducts,
            accessToken = accessTokenForUpdate,
            hostedLink = HostedLinkConfig(completionRedirectUri = completionRedirectUri, isMobileApp = true),
            transactions = if (!update && "transactions" in purpose.products) TransactionsConfig(730) else null,
        )
        val response = post("/link/token/create", request, LinkTokenCreateRequest.serializer(), LinkTokenCreateResponse.serializer())
        return PlaidLinkToken(response.linkToken, response.hostedLinkUrl, response.expiration)
    }

    /** Items the user finished linking in a Hosted Link session (empty until they're done). */
    suspend fun linkedItems(linkToken: String): List<PlaidLinkedItem> {
        val response = post("/link/token/get", LinkTokenGetRequest(linkToken), LinkTokenGetRequest.serializer(), LinkTokenGetResponse.serializer())
        return response.linkSessions.flatMap { session ->
            val fromResults = session.results?.itemAddResults.orEmpty().map {
                PlaidLinkedItem(it.publicToken, it.institution?.name, it.institution?.institutionId)
            }
            fromResults.ifEmpty {
                listOfNotNull(session.onSuccess?.let { PlaidLinkedItem(it.publicToken, it.metadata?.institution?.name, it.metadata?.institution?.institutionId) })
            }
        }.distinctBy { it.publicToken }
    }

    suspend fun exchangePublicToken(publicToken: String): PlaidItemAccess {
        val response = post(
            "/item/public_token/exchange", PublicTokenExchangeRequest(publicToken),
            PublicTokenExchangeRequest.serializer(), PublicTokenExchangeResponse.serializer(),
        )
        return PlaidItemAccess(response.accessToken, response.itemId)
    }

    suspend fun removeItem(accessToken: String) {
        post("/item/remove", AccessTokenRequest(accessToken), AccessTokenRequest.serializer(), PlaidError.serializer())
    }

    suspend fun accounts(accessToken: String): AccountsGetResponse =
        post("/accounts/get", AccessTokenRequest(accessToken), AccessTokenRequest.serializer(), AccountsGetResponse.serializer())

    /** Pages through /transactions/sync from [cursor] until there's nothing more. */
    suspend fun syncTransactions(accessToken: String, cursor: String?): TransactionsSyncResponse {
        var attempt = 0
        while (true) {
            try {
                return syncPages(accessToken, cursor)
            } catch (e: ConnectorException.Provider) {
                // Data changed mid-pagination: Plaid asks clients to restart from the original cursor.
                if (e.code == "TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION" && attempt++ < 3) continue
                throw e
            }
        }
    }

    private suspend fun syncPages(accessToken: String, start: String?): TransactionsSyncResponse {
        var cursor = start
        val added = mutableListOf<PlaidTransaction>()
        val modified = mutableListOf<PlaidTransaction>()
        val removed = mutableListOf<RemovedTransaction>()
        var status: String? = null
        var pages = 0
        do {
            val page = post(
                "/transactions/sync", TransactionsSyncRequest(accessToken, cursor?.ifEmpty { null }),
                TransactionsSyncRequest.serializer(), TransactionsSyncResponse.serializer(),
            )
            added += page.added
            modified += page.modified
            removed += page.removed
            cursor = page.nextCursor
            status = page.updateStatus
        } while (page.hasMore && ++pages < 50)
        return TransactionsSyncResponse(added, modified, removed, cursor.orEmpty(), false, status)
    }

    suspend fun liabilities(accessToken: String): LiabilitiesGetResponse? = optionalProduct {
        post("/liabilities/get", AccessTokenRequest(accessToken), AccessTokenRequest.serializer(), LiabilitiesGetResponse.serializer())
    }

    suspend fun holdings(accessToken: String): HoldingsGetResponse? = optionalProduct {
        post("/investments/holdings/get", AccessTokenRequest(accessToken), AccessTokenRequest.serializer(), HoldingsGetResponse.serializer())
    }

    /** Runs a call for a product the item may not support, returning null instead of failing the whole sync. */
    private suspend fun <T> optionalProduct(block: suspend () -> T): T? = try {
        block()
    } catch (e: ConnectorException.Provider) {
        val ignorable = setOf(
            "PRODUCTS_NOT_SUPPORTED", "PRODUCT_NOT_READY", "NO_LIABILITY_ACCOUNTS", "NO_INVESTMENT_ACCOUNTS",
            "NO_INVESTMENT_AUTH_ACCOUNTS", "ADDITIONAL_CONSENT_REQUIRED", "INVALID_PRODUCT", "PRODUCT_NOT_ENABLED",
        )
        if (e.code in ignorable) null else throw e
    }

    /**
     * Full sync for one Item: balances, new/changed/removed transactions since [cursor],
     * debt details (APR, minimum payment, due date) and investment holdings.
     */
    suspend fun sync(accessToken: String, cursor: String?, products: Set<String>, institutionName: String?): SyncResult {
        val warnings = mutableListOf<String>()
        val accountsResponse = accounts(accessToken)
        val institution = institutionName ?: accountsResponse.item?.institutionName

        val liabilities = if ("liabilities" in products) liabilities(accessToken)?.liabilities else null
        val credit = liabilities?.credit.orEmpty().associateBy { it.accountId }
        val student = liabilities?.student.orEmpty().associateBy { it.accountId }
        val mortgage = liabilities?.mortgage.orEmpty().associateBy { it.accountId }

        val accounts = accountsResponse.accounts.map { account ->
            val type = PlaidMapping.accountType(account.type, account.subtype)
            var apr: Double? = null
            var minimum: Long? = null
            var dueDay: Int? = null
            credit[account.accountId]?.let { c ->
                apr = (c.aprs.firstOrNull { it.aprType == "purchase_apr" } ?: c.aprs.maxByOrNull { it.aprPercentage ?: 0.0 })?.aprPercentage
                minimum = c.minimumPaymentAmount?.let(Money::fromDollars)
                dueDay = dayOf(c.nextPaymentDueDate)
            }
            student[account.accountId]?.let { s ->
                apr = s.interestRatePercentage
                minimum = s.minimumPaymentAmount?.let(Money::fromDollars)
                dueDay = dayOf(s.nextPaymentDueDate)
            }
            mortgage[account.accountId]?.let { m ->
                apr = m.interestRate?.percentage
                minimum = m.nextMonthlyPayment?.let(Money::fromDollars)
                dueDay = dayOf(m.nextPaymentDueDate)
            }
            val current = Money.fromDollars(account.balances.current ?: account.balances.available ?: 0.0)
            SyncedAccount(
                externalId = account.accountId,
                name = sanitize(account.name, 80),
                type = type,
                // Plaid already reports debts as positive amounts owed.
                balance = current,
                available = if (type.isLiability) null else account.balances.available?.let(Money::fromDollars),
                creditLimit = account.balances.limit?.let(Money::fromDollars),
                currency = account.balances.isoCurrencyCode ?: "USD",
                mask = account.mask,
                institution = institution?.let { sanitize(it, 80) },
                apr = apr,
                minimumPayment = minimum,
                paymentDueDay = dueDay,
            )
        }

        var nextCursor = cursor
        val transactions = mutableListOf<SyncedTransaction>()
        val removed = mutableListOf<String>()
        if ("transactions" in products) {
            val sync = syncTransactions(accessToken, cursor)
            nextCursor = sync.nextCursor
            (sync.added + sync.modified).mapNotNullTo(transactions) { it.toSynced() }
            sync.removed.mapTo(removed) { it.transactionId }
            if (sync.updateStatus == "NOT_READY" && sync.added.isEmpty()) {
                warnings += "Your bank is still preparing transaction history. It will appear on the next sync."
            }
        }

        val holdings = if ("investments" in products) {
            holdings(accessToken)?.let { response ->
                val securities = response.securities.associateBy { it.securityId }
                response.holdings.map { h ->
                    val security = securities[h.securityId]
                    SyncedHolding(
                        accountExternalId = h.accountId,
                        securityId = h.securityId,
                        name = sanitize(security?.name ?: security?.tickerSymbol ?: "Holding", 80),
                        ticker = security?.tickerSymbol,
                        quantity = h.quantity,
                        value = Money.fromDollars(h.institutionValue),
                        costBasis = h.costBasis?.let(Money::fromDollars),
                    )
                }
            }.orEmpty()
        } else {
            emptyList()
        }

        return SyncResult(
            accounts = accounts,
            transactions = transactions,
            removedTransactionIds = removed,
            holdings = holdings,
            cursor = nextCursor,
            warnings = warnings,
        )
    }

    private fun PlaidTransaction.toSynced(): SyncedTransaction? {
        val parsedDate = parseDate(authorizedDate) ?: parseDate(date) ?: return null
        val description = sanitize(name ?: merchantName ?: "Transaction", 200)
        return SyncedTransaction(
            externalId = transactionId,
            accountExternalId = accountId,
            date = parsedDate,
            // Plaid uses positive amounts for money leaving the account; the app uses negative.
            amount = -Money.fromDollars(amount),
            description = description,
            merchant = merchantName?.let { sanitize(it, 80) },
            pending = pending,
            categoryHint = PlaidMapping.category(personalFinanceCategory?.primary, personalFinanceCategory?.detailed),
        )
    }

    private fun parseDate(value: String?): LocalDate? = try {
        value?.let(LocalDate::parse)
    } catch (e: DateTimeParseException) {
        null
    }

    private fun dayOf(date: String?): Int? = parseDate(date)?.dayOfMonth

    private suspend fun <Req, Res> post(path: String, body: Req, requestSerializer: KSerializer<Req>, responseSerializer: KSerializer<Res>): Res =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .header("PLAID-CLIENT-ID", credentials.clientId)
                .header("PLAID-SECRET", credentials.secret)
                .header("Plaid-Version", "2020-09-14")
                .post(json.encodeToString(requestSerializer, body).toRequestBody(mediaType))
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw toException(response.code, text)
                    json.decodeFromString(responseSerializer, text)
                }
            } catch (e: ConnectorException) {
                throw e
            } catch (e: kotlinx.serialization.SerializationException) {
                throw ConnectorException.Provider("Plaid returned data the app couldn't read.")
            } catch (e: IOException) {
                throw ConnectorException.Network("Couldn't reach Plaid. Check your connection and try again.", e)
            }
        }

    private fun toException(status: Int, body: String): ConnectorException {
        val error = runCatching { json.decodeFromString(PlaidError.serializer(), body) }.getOrNull()
        val code = error?.errorCode
        val message = sanitize(error?.displayMessage ?: error?.errorMessage ?: "Plaid returned an error ($status).")
        return when {
            code == "ITEM_LOGIN_REQUIRED" || code == "INVALID_ACCESS_TOKEN" || code == "ITEM_NOT_FOUND" || code == "ACCESS_NOT_GRANTED" ->
                ConnectorException.ReauthRequired("Your bank needs you to sign in again. Tap reconnect to fix it.")
            code == "INVALID_API_KEYS" || code == "INVALID_CLIENT_ID" || code == "INVALID_SECRET" ->
                ConnectorException.InvalidInput("Plaid rejected your API keys. Check the client ID, secret and environment in Settings.")
            status == 429 || error?.errorType == "RATE_LIMIT_EXCEEDED" -> ConnectorException.RateLimited(message)
            else -> ConnectorException.Provider(message, code)
        }
    }
}
