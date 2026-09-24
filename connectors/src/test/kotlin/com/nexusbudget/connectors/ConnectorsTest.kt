package com.nexusbudget.connectors

import com.nexusbudget.connectors.plaid.PlaidClient
import com.nexusbudget.connectors.plaid.PlaidCredentials
import com.nexusbudget.connectors.plaid.PlaidEnvironment
import com.nexusbudget.connectors.plaid.PlaidLinkPurpose
import com.nexusbudget.connectors.simplefin.SimpleFinClient
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Categories
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConnectorsTest {

    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun `simplefin claim and fetch`() = runTest {
        val accessUrl = server.url("/simplefin").newBuilder().username("demo").password("secret").build().toString()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "POST" && request.path == "/simplefin/claim/abc" -> MockResponse().setBody(accessUrl)
                request.path!!.startsWith("/simplefin/accounts") -> {
                    assertEquals(okhttp3.Credentials.basic("demo", "secret"), request.getHeader("Authorization"))
                    assertTrue(request.path!!.contains("pending=1"))
                    MockResponse().setBody(SIMPLEFIN_JSON)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val client = SimpleFinClient(http, requireHttps = false, zone = ZoneOffset.UTC)
        val token = Base64.getEncoder().encodeToString(server.url("/simplefin/claim/abc").toString().toByteArray())
        val claimed = client.claim(token)
        assertEquals(accessUrl, claimed)

        val result = client.sync(claimed, Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals(2, result.accounts.size)
        val card = result.accounts.first { it.externalId == "card-1" }
        assertEquals(AccountType.CREDIT_CARD, card.type)
        assertEquals(512_34, card.balance)
        val checking = result.accounts.first { it.externalId == "chk-1" }
        assertEquals(AccountType.CHECKING, checking.type)
        assertEquals(1_200_50, checking.balance)
        assertEquals(1_100_00, checking.available)
        assertEquals("My Bank", checking.institution)
        val txn = result.transactions.first { it.externalId == "chk-1/t1" }
        assertEquals(-45_67, txn.amount)
        assertEquals(LocalDate.of(2026, 2, 1), txn.date)
        assertTrue(result.transactions.first { it.externalId == "chk-1/t2" }.pending)
        assertEquals(listOf("Reconnect your card account."), result.warnings)
    }

    @Test
    fun `simplefin reports revoked access`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val client = SimpleFinClient(http, requireHttps = false)
        val url = server.url("/simplefin").newBuilder().username("u").password("p").build().toString()
        assertFailsWith<ConnectorException.ReauthRequired> { client.fetchAccounts(url, null) }
    }

    @Test
    fun `simplefin rejects insecure tokens`() {
        val client = SimpleFinClient(http)
        val token = Base64.getEncoder().encodeToString("http://example.com/claim/x".toByteArray())
        assertFailsWith<ConnectorException.InvalidInput> { client.decodeSetupToken(token) }
        assertFailsWith<ConnectorException.InvalidInput> { client.decodeSetupToken("not a token!!") }
    }

    @Test
    fun `plaid sync maps accounts, liabilities and transactions`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("id", request.getHeader("PLAID-CLIENT-ID"))
                return when (request.path) {
                    "/accounts/get" -> MockResponse().setBody(PLAID_ACCOUNTS)
                    "/liabilities/get" -> MockResponse().setBody(PLAID_LIABILITIES)
                    "/transactions/sync" -> {
                        val body = request.body.readUtf8()
                        if (!body.contains("cursor")) MockResponse().setBody(PLAID_SYNC_PAGE_1) else MockResponse().setBody(PLAID_SYNC_PAGE_2)
                    }
                    else -> MockResponse().setResponseCode(400).setBody("""{"error_type":"INVALID_REQUEST","error_code":"UNKNOWN","error_message":"no"}""")
                }
            }
        }
        val client = PlaidClient(http, PlaidCredentials("id", "secret", PlaidEnvironment.SANDBOX), server.url("/").toString())
        val result = client.sync("access", null, PlaidLinkPurpose.BANKING.allProducts, "Test Bank")

        val loan = result.accounts.first { it.externalId == "loan" }
        assertEquals(AccountType.STUDENT_LOAN, loan.type)
        assertEquals(20_000_00, loan.balance)
        assertEquals(5.25, loan.apr)
        assertEquals(210_00, loan.minimumPayment)
        assertEquals(15, loan.paymentDueDay)
        val card = result.accounts.first { it.externalId == "card" }
        assertEquals(22.99, card.apr)
        assertEquals(6_000_00, card.creditLimit)

        assertEquals(2, result.transactions.size)
        val coffee = result.transactions.first { it.externalId == "t1" }
        assertEquals(-4_75, coffee.amount)
        assertEquals(Categories.COFFEE, coffee.categoryHint)
        val pay = result.transactions.first { it.externalId == "t2" }
        assertEquals(1_500_00, pay.amount)
        assertEquals(Categories.PAYCHECK, pay.categoryHint)
        assertEquals(listOf("old"), result.removedTransactionIds)
        assertEquals("cursor-2", result.cursor)
    }

    @Test
    fun `plaid login errors ask for reconnection`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_type":"ITEM_ERROR","error_code":"ITEM_LOGIN_REQUIRED","error_message":"login"}"""))
        val client = PlaidClient(http, PlaidCredentials("id", "secret", PlaidEnvironment.SANDBOX), server.url("/").toString())
        assertFailsWith<ConnectorException.ReauthRequired> { client.accounts("access") }
    }

    @Test
    fun `account names map to sensible types`() {
        assertEquals(AccountType.RETIREMENT, AccountTypeGuesser.guess("Roth IRA", 100))
        assertEquals(AccountType.STUDENT_LOAN, AccountTypeGuesser.guess("Direct Loan - Subsidized", -100))
        assertEquals(AccountType.CHECKING, AccountTypeGuesser.guess("Miracle Account", 100))
        assertEquals(AccountType.CREDIT_CARD, AccountTypeGuesser.guess("Mystery", -100))
        assertEquals(AccountType.BROKERAGE, AccountTypeGuesser.guess("Individual", 100, hasHoldings = true))
    }

    companion object {
        private const val SIMPLEFIN_JSON = """
        {
          "errors": ["Reconnect your card account."],
          "accounts": [
            {
              "org": {"domain": "mybank.com", "name": "My Bank", "sfin-url": "https://sfin.mybank.com"},
              "id": "chk-1", "name": "Everyday Checking", "currency": "USD",
              "balance": "1200.50", "available-balance": "1100.00", "balance-date": 1767225600,
              "transactions": [
                {"id": "t1", "posted": 1769904000, "amount": "-45.67", "description": "GREENLEAF GROCERY"},
                {"id": "t2", "posted": 0, "amount": "-12.00", "description": "CORNER BISTRO", "pending": true}
              ]
            },
            {
              "org": {"domain": "mybank.com", "sfin-url": "https://sfin.mybank.com"},
              "id": "card-1", "name": "Rewards Visa", "currency": "USD",
              "balance": "-512.34", "balance-date": 1767225600, "transactions": []
            }
          ]
        }
        """

        private const val PLAID_ACCOUNTS = """
        {"accounts": [
          {"account_id": "chk", "balances": {"available": 900.1, "current": 950.1, "iso_currency_code": "USD"}, "mask": "0000", "name": "Checking", "type": "depository", "subtype": "checking"},
          {"account_id": "card", "balances": {"available": 5000, "current": 1000, "limit": 6000, "iso_currency_code": "USD"}, "mask": "1111", "name": "Card", "type": "credit", "subtype": "credit card"},
          {"account_id": "loan", "balances": {"current": 20000, "iso_currency_code": "USD"}, "name": "Student Loan", "type": "loan", "subtype": "student"}
        ], "item": {"item_id": "item", "institution_id": "ins_1"}, "request_id": "r"}
        """

        private const val PLAID_LIABILITIES = """
        {"accounts": [], "liabilities": {
          "credit": [{"account_id": "card", "aprs": [{"apr_percentage": 22.99, "apr_type": "purchase_apr"}, {"apr_percentage": 29.99, "apr_type": "cash_apr"}], "minimum_payment_amount": 35, "next_payment_due_date": "2026-05-28"}],
          "student": [{"account_id": "loan", "interest_rate_percentage": 5.25, "minimum_payment_amount": 210, "next_payment_due_date": "2026-05-15", "loan_name": "Direct"}],
          "mortgage": []
        }, "request_id": "r"}
        """

        private const val PLAID_SYNC_PAGE_1 = """
        {"added": [{"transaction_id": "t1", "account_id": "chk", "amount": 4.75, "date": "2026-05-01", "name": "BEAN THERE", "merchant_name": "Bean There", "pending": false,
                    "personal_finance_category": {"primary": "FOOD_AND_DRINK", "detailed": "FOOD_AND_DRINK_COFFEE"}}],
         "modified": [], "removed": [], "next_cursor": "cursor-1", "has_more": true, "request_id": "r", "accounts": [], "transactions_update_status": "HISTORICAL_UPDATE_COMPLETE"}
        """

        private const val PLAID_SYNC_PAGE_2 = """
        {"added": [{"transaction_id": "t2", "account_id": "chk", "amount": -1500, "date": "2026-05-02", "name": "ACME PAYROLL", "pending": false,
                    "personal_finance_category": {"primary": "INCOME", "detailed": "INCOME_WAGES"}}],
         "modified": [], "removed": [{"transaction_id": "old"}], "next_cursor": "cursor-2", "has_more": false, "request_id": "r", "accounts": []}
        """
    }
}
