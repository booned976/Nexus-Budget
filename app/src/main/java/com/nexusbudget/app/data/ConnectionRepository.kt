package com.nexusbudget.app.data

import androidx.room.withTransaction
import com.nexusbudget.app.data.db.AccountEntity
import com.nexusbudget.app.data.db.ConnectionEntity
import com.nexusbudget.app.data.db.HoldingEntity
import com.nexusbudget.app.data.db.NexusDatabase
import com.nexusbudget.connectors.ConnectorException
import com.nexusbudget.connectors.SyncResult
import com.nexusbudget.connectors.plaid.PlaidClient
import com.nexusbudget.connectors.plaid.PlaidCredentials
import com.nexusbudget.connectors.plaid.PlaidEnvironment
import com.nexusbudget.connectors.plaid.PlaidLinkPurpose
import com.nexusbudget.connectors.plaid.PlaidLinkToken
import com.nexusbudget.connectors.simplefin.SimpleFinClient
import com.nexusbudget.core.engine.MerchantNormalizer
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class Provider(val label: String) { SIMPLEFIN("SimpleFIN"), PLAID("Plaid") }

enum class ConnectionStatus { OK, NEEDS_REAUTH, ERROR }

data class Connection(
    val id: String,
    val provider: Provider,
    val displayName: String,
    val status: ConnectionStatus,
    val lastSync: Instant?,
    val message: String?,
    val products: Set<String>,
)

data class SyncSummary(val connections: Int, val newTransactions: Int, val problems: List<String>)

/**
 * Owns bank connections: linking, syncing and removal. Every provider used here is read-only;
 * access tokens are kept encrypted in [SecureStore].
 */
class ConnectionRepository(
    private val db: NexusDatabase,
    private val secure: SecureStore,
    private val settings: SettingsRepository,
    private val finance: FinanceRepository,
    private val http: OkHttpClient,
) {
    private val simpleFin = SimpleFinClient(http)
    private val mutex = Mutex()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    val connections: Flow<List<Connection>> = db.connections().observeAll().map { list -> list.map { it.toConnection() } }

    private fun ConnectionEntity.toConnection() = Connection(
        id = id,
        provider = Provider.entries.firstOrNull { it.name == provider } ?: Provider.SIMPLEFIN,
        displayName = displayName,
        status = ConnectionStatus.entries.firstOrNull { it.name == status } ?: ConnectionStatus.OK,
        lastSync = lastSync?.let(Instant::ofEpochMilli),
        message = message,
        products = products?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
    )

    // SimpleFIN

    /** Claims a SimpleFIN setup token, stores the resulting read-only access URL, and runs the first sync. */
    suspend fun connectSimpleFin(setupToken: String): Connection {
        val accessUrl = simpleFin.claim(setupToken)
        leaveDemoMode()
        val id = "sfin-${UUID.randomUUID()}"
        secure.put(SecureStore.connectionSecret(id), accessUrl)
        val entity = ConnectionEntity(id, Provider.SIMPLEFIN.name, "SimpleFIN", ConnectionStatus.OK.name, null, null, null, null, System.currentTimeMillis())
        db.connections().upsert(entity)
        mutex.withLock { syncOne(entity) }
        finance.recordSnapshot()
        return db.connections().get(id)!!.toConnection()
    }

    /** Replaces the access URL of an existing SimpleFIN connection after its access was revoked. */
    suspend fun reconnectSimpleFin(connectionId: String, setupToken: String) {
        val accessUrl = simpleFin.claim(setupToken)
        secure.put(SecureStore.connectionSecret(connectionId), accessUrl)
        sync(connectionId)
    }

    // Plaid

    fun plaidConfigured(): Boolean = secure.has(SecureStore.PLAID_CLIENT_ID) && secure.has(SecureStore.PLAID_SECRET)

    fun plaidClientId(): String = secure.get(SecureStore.PLAID_CLIENT_ID).orEmpty()

    suspend fun savePlaidKeys(clientId: String, secret: String, environment: PlaidEnvironment) {
        secure.put(SecureStore.PLAID_CLIENT_ID, clientId.trim())
        if (secret.isNotBlank()) secure.put(SecureStore.PLAID_SECRET, secret.trim())
        settings.setPlaidEnvironment(environment)
    }

    private suspend fun plaid(): PlaidClient {
        val clientId = secure.get(SecureStore.PLAID_CLIENT_ID)
        val secret = secure.get(SecureStore.PLAID_SECRET)
        if (clientId.isNullOrBlank() || secret.isNullOrBlank()) {
            throw ConnectorException.InvalidInput("Add your Plaid API keys in Settings first.")
        }
        return PlaidClient(http, PlaidCredentials(clientId, secret, settings.current().plaidEnvironment))
    }

    /** Creates a Hosted Link session. Open the returned URL in a browser tab; the user returns via [PLAID_REDIRECT]. */
    suspend fun startPlaidLink(purpose: PlaidLinkPurpose, reconnectConnectionId: String? = null): PlaidLinkToken {
        val client = plaid()
        val accessToken = reconnectConnectionId?.let { secure.get(SecureStore.connectionSecret(it)) }
        val token = client.createLinkToken(settings.installId(), purpose, PLAID_REDIRECT, accessToken)
        if (token.hostedLinkUrl.isNullOrBlank()) throw ConnectorException.Provider("Plaid didn't return a link. Check that your Plaid account can use Hosted Link.")
        secure.put(PENDING_LINK, listOf(token.linkToken, purpose.name, reconnectConnectionId.orEmpty()).joinToString("|"))
        return token
    }

    fun hasPendingPlaidLink(): Boolean = secure.has(PENDING_LINK)

    /** Finishes a Hosted Link session: exchanges each new public token and syncs the new connections. */
    suspend fun completePlaidLink(): List<Connection> {
        val pending = secure.get(PENDING_LINK)?.split("|") ?: return emptyList()
        val linkToken = pending[0]
        val purpose = PlaidLinkPurpose.entries.firstOrNull { it.name == pending.getOrNull(1) } ?: PlaidLinkPurpose.BANKING
        val reconnectId = pending.getOrNull(2)?.takeIf { it.isNotBlank() }
        val client = plaid()
        val items = client.linkedItems(linkToken)
        if (reconnectId != null) {
            secure.remove(PENDING_LINK)
            sync(reconnectId)
            return listOfNotNull(db.connections().get(reconnectId)?.toConnection())
        }
        if (items.isEmpty()) return emptyList()
        secure.remove(PENDING_LINK)
        leaveDemoMode()
        val created = mutableListOf<Connection>()
        for (item in items) {
            val access = client.exchangePublicToken(item.publicToken)
            val id = "plaid-${access.itemId}"
            secure.put(SecureStore.connectionSecret(id), access.accessToken)
            val entity = ConnectionEntity(
                id = id,
                provider = Provider.PLAID.name,
                displayName = item.institutionName ?: "Plaid connection",
                status = ConnectionStatus.OK.name,
                lastSync = null,
                message = null,
                cursor = null,
                products = purpose.allProducts.joinToString(","),
                createdAt = System.currentTimeMillis(),
            )
            db.connections().upsert(entity)
            mutex.withLock { syncOne(entity) }
            db.connections().get(id)?.let { created += it.toConnection() }
        }
        finance.recordSnapshot()
        return created
    }

    fun cancelPlaidLink() = secure.remove(PENDING_LINK)

    // Syncing

    suspend fun syncAll(): SyncSummary {
        val problems = mutableListOf<String>()
        var added = 0
        val all = db.connections().all()
        mutex.withLock {
            _syncing.value = true
            try {
                for (connection in all) {
                    runCatching { added += syncOne(connection) }.onFailure { problems += "${connection.displayName}: ${it.message}" }
                }
            } finally {
                _syncing.value = false
            }
        }
        finance.recordSnapshot()
        settings.setLastSync(System.currentTimeMillis())
        return SyncSummary(all.size, added, problems)
    }

    suspend fun sync(connectionId: String) {
        val connection = db.connections().get(connectionId) ?: return
        mutex.withLock {
            _syncing.value = true
            try {
                syncOne(connection)
            } finally {
                _syncing.value = false
            }
        }
        finance.recordSnapshot()
    }

    /** Syncs one connection and records its status. Returns the number of new transactions. */
    private suspend fun syncOne(connection: ConnectionEntity): Int {
        val secret = secure.get(SecureStore.connectionSecret(connection.id))
        if (secret == null) {
            db.connections().upsert(connection.copy(status = ConnectionStatus.NEEDS_REAUTH.name, message = "Saved credentials are missing. Please reconnect."))
            return 0
        }
        return try {
            val result = when (connection.provider) {
                Provider.PLAID.name -> plaid().sync(
                    secret,
                    connection.cursor,
                    connection.products?.split(',')?.toSet() ?: PlaidLinkPurpose.BANKING.allProducts,
                    connection.displayName,
                )
                else -> {
                    val start = connection.lastSync?.let { Instant.ofEpochMilli(it).minus(14, ChronoUnit.DAYS) }
                        ?: Instant.now().minus(90, ChronoUnit.DAYS)
                    simpleFin.sync(secret, start)
                }
            }
            val added = apply(connection, result)
            val name = if (connection.provider == Provider.SIMPLEFIN.name) {
                result.accounts.mapNotNull { it.institution }.distinct().take(3).joinToString(", ").ifBlank { connection.displayName }
            } else {
                connection.displayName
            }
            db.connections().upsert(
                connection.copy(
                    displayName = name,
                    status = ConnectionStatus.OK.name,
                    lastSync = System.currentTimeMillis(),
                    message = result.warnings.joinToString("\n").ifBlank { null },
                    cursor = result.cursor ?: connection.cursor,
                ),
            )
            added
        } catch (e: ConnectorException.ReauthRequired) {
            db.connections().upsert(connection.copy(status = ConnectionStatus.NEEDS_REAUTH.name, message = e.message))
            throw e
        } catch (e: IOException) {
            db.connections().upsert(connection.copy(status = ConnectionStatus.ERROR.name, message = e.message))
            throw e
        }
    }

    private suspend fun apply(connection: ConnectionEntity, result: SyncResult): Int {
        val now = System.currentTimeMillis()
        val existing = db.accounts().forConnection(connection.id).associateBy { it.externalId }
        val accountIds = mutableMapOf<String, String>()
        val accounts = result.accounts.map { synced ->
            val prior = existing[synced.externalId]
            val id = prior?.id ?: "${connection.id}:${synced.externalId}"
            accountIds[synced.externalId] = id
            if (prior == null) {
                AccountEntity(
                    id = id, name = synced.name, type = synced.type.name, balance = synced.balance,
                    institution = synced.institution, available = synced.available, creditLimit = synced.creditLimit,
                    currency = synced.currency, mask = synced.mask, connectionId = connection.id, externalId = synced.externalId,
                    apr = synced.apr, minimumPayment = synced.minimumPayment, paymentDueDay = synced.paymentDueDay,
                    isHidden = false, includeInNetWorth = true, lastUpdated = now,
                )
            } else {
                val priorType = AccountType.entries.firstOrNull { it.name == prior.type } ?: synced.type
                // If the user corrected an asset/debt guess, flip the provider's sign to match.
                val balance = if (prior.userEditedType && priorType.isLiability != synced.type.isLiability) -synced.balance else synced.balance
                prior.copy(
                    type = if (prior.userEditedType) prior.type else synced.type.name,
                    balance = balance,
                    available = if (priorType.isLiability) null else synced.available,
                    creditLimit = synced.creditLimit ?: prior.creditLimit,
                    institution = synced.institution ?: prior.institution,
                    mask = synced.mask ?: prior.mask,
                    apr = synced.apr ?: prior.apr,
                    minimumPayment = synced.minimumPayment ?: prior.minimumPayment,
                    paymentDueDay = synced.paymentDueDay ?: prior.paymentDueDay,
                    lastUpdated = now,
                )
            }
        }
        db.accounts().upsertAll(accounts)

        val idFor = { externalId: String -> "${connection.id}:$externalId" }
        val incomingIds = result.transactions.map { idFor(it.externalId) }
        val stored = incomingIds.chunked(500).flatMap { db.transactions().byIds(it) }.associateBy { it.id }
        val fresh = mutableListOf<Transaction>()
        val hints = mutableMapOf<String, String>()
        val updates = result.transactions.mapNotNull { synced ->
            val accountId = accountIds[synced.accountExternalId] ?: return@mapNotNull null
            val id = idFor(synced.externalId)
            val prior = stored[id]
            if (prior != null) {
                prior.copy(date = synced.date.toEpochDay(), amount = synced.amount, description = synced.description, pending = synced.pending)
            } else {
                fresh += Transaction(
                    id = id,
                    accountId = accountId,
                    date = synced.date,
                    amount = synced.amount,
                    description = synced.description,
                    merchant = synced.merchant ?: MerchantNormalizer.displayName(synced.description),
                    pending = synced.pending,
                )
                synced.categoryHint?.let { hints[id] = it }
                null
            }
        }
        db.transactions().upsertAll(updates)
        finance.insertProcessed(fresh, hints)

        val removed = result.removedTransactionIds.map(idFor).toMutableSet()
        result.replacesPendingSince?.let { since ->
            val pending = db.transactions().pendingIds(accountIds.values.toList(), since.toEpochDay())
            removed += pending.filter { it !in incomingIds }
        }
        removed.chunked(500).forEach { db.transactions().deleteIds(it) }

        if (result.holdings.isNotEmpty()) {
            val holdings = result.holdings.mapNotNull { h ->
                val accountId = accountIds[h.accountExternalId] ?: return@mapNotNull null
                HoldingEntity(accountId, h.securityId, h.name, h.ticker, h.quantity, h.value, h.costBasis)
            }
            db.withTransaction {
                db.holdings().deleteForAccounts(holdings.map { it.accountId }.distinct())
                db.holdings().insertAll(holdings)
            }
        }
        return fresh.size
    }

    /** Disconnects a connection. Plaid items are also removed on Plaid's side so the token stops working. */
    suspend fun remove(connectionId: String, deleteData: Boolean) {
        val connection = db.connections().get(connectionId) ?: return
        val secret = secure.get(SecureStore.connectionSecret(connectionId))
        if (connection.provider == Provider.PLAID.name && secret != null) {
            runCatching { plaid().removeItem(secret) }
        }
        secure.remove(SecureStore.connectionSecret(connectionId))
        db.withTransaction {
            if (deleteData) {
                db.accounts().forConnection(connectionId).forEach { account ->
                    db.transactions().deleteForAccount(account.id)
                    db.holdings().deleteForAccounts(listOf(account.id))
                }
                db.accounts().deleteForConnection(connectionId)
            } else {
                // Keep the history as manual accounts.
                db.accounts().forConnection(connectionId).forEach { db.accounts().upsert(it.copy(connectionId = null)) }
            }
            db.connections().delete(connectionId)
        }
        finance.recordSnapshot()
    }

    /** Revokes and forgets every bank connection's credentials (API keys are kept). */
    suspend fun disconnectAll() {
        db.connections().all().forEach { connection ->
            val secret = secure.get(SecureStore.connectionSecret(connection.id))
            if (connection.provider == Provider.PLAID.name && secret != null) runCatching { plaid().removeItem(secret) }
            secure.remove(SecureStore.connectionSecret(connection.id))
        }
        secure.remove(PENDING_LINK)
    }

    private suspend fun leaveDemoMode() {
        if (settings.current().demoMode) finance.clearFinancialData()
    }

    companion object {
        const val PLAID_REDIRECT = "nexusbudget://plaid-complete"
        private const val PENDING_LINK = "plaid_pending_link"
        const val SIMPLEFIN_CREATE_URL = "https://bridge.simplefin.org/simplefin/create"
    }
}
