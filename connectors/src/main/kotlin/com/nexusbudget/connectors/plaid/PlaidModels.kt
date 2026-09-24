package com.nexusbudget.connectors.plaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaidError(
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("display_message") val displayMessage: String? = null,
)

@Serializable
data class LinkTokenUser(@SerialName("client_user_id") val clientUserId: String)

@Serializable
data class HostedLinkConfig(
    @SerialName("completion_redirect_uri") val completionRedirectUri: String? = null,
    @SerialName("is_mobile_app") val isMobileApp: Boolean = true,
)

@Serializable
data class TransactionsConfig(@SerialName("days_requested") val daysRequested: Int = 730)

@Serializable
data class LinkTokenCreateRequest(
    @SerialName("client_name") val clientName: String,
    val language: String = "en",
    @SerialName("country_codes") val countryCodes: List<String> = listOf("US"),
    val user: LinkTokenUser,
    val products: List<String>? = null,
    @SerialName("optional_products") val optionalProducts: List<String>? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("hosted_link") val hostedLink: HostedLinkConfig? = null,
    val transactions: TransactionsConfig? = null,
)

@Serializable
data class LinkTokenCreateResponse(
    @SerialName("link_token") val linkToken: String,
    val expiration: String? = null,
    @SerialName("hosted_link_url") val hostedLinkUrl: String? = null,
)

@Serializable
data class LinkTokenGetRequest(@SerialName("link_token") val linkToken: String)

@Serializable
data class LinkInstitution(val name: String? = null, @SerialName("institution_id") val institutionId: String? = null)

@Serializable
data class LinkItemAddResult(
    @SerialName("public_token") val publicToken: String,
    val institution: LinkInstitution? = null,
)

@Serializable
data class LinkSessionResults(@SerialName("item_add_results") val itemAddResults: List<LinkItemAddResult> = emptyList())

@Serializable
data class LinkSessionSuccessMetadata(val institution: LinkInstitution? = null)

@Serializable
data class LinkSessionSuccess(
    @SerialName("public_token") val publicToken: String,
    val metadata: LinkSessionSuccessMetadata? = null,
)

@Serializable
data class LinkSession(
    @SerialName("link_session_id") val linkSessionId: String,
    @SerialName("finished_at") val finishedAt: String? = null,
    val results: LinkSessionResults? = null,
    @SerialName("on_success") val onSuccess: LinkSessionSuccess? = null,
)

@Serializable
data class LinkTokenGetResponse(@SerialName("link_sessions") val linkSessions: List<LinkSession> = emptyList())

@Serializable
data class PublicTokenExchangeRequest(@SerialName("public_token") val publicToken: String)

@Serializable
data class PublicTokenExchangeResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("item_id") val itemId: String,
)

@Serializable
data class AccessTokenRequest(@SerialName("access_token") val accessToken: String)

@Serializable
data class PlaidBalances(
    val available: Double? = null,
    val current: Double? = null,
    val limit: Double? = null,
    @SerialName("iso_currency_code") val isoCurrencyCode: String? = null,
)

@Serializable
data class PlaidAccount(
    @SerialName("account_id") val accountId: String,
    val balances: PlaidBalances = PlaidBalances(),
    val mask: String? = null,
    val name: String = "Account",
    @SerialName("official_name") val officialName: String? = null,
    val type: String = "other",
    val subtype: String? = null,
)

@Serializable
data class PlaidItem(
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("institution_id") val institutionId: String? = null,
    @SerialName("institution_name") val institutionName: String? = null,
)

@Serializable
data class AccountsGetResponse(val accounts: List<PlaidAccount> = emptyList(), val item: PlaidItem? = null)

@Serializable
data class TransactionsSyncRequest(
    @SerialName("access_token") val accessToken: String,
    val cursor: String? = null,
    val count: Int = 500,
)

@Serializable
data class PersonalFinanceCategory(val primary: String? = null, val detailed: String? = null)

@Serializable
data class PlaidTransaction(
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("account_id") val accountId: String,
    val amount: Double,
    val date: String,
    @SerialName("authorized_date") val authorizedDate: String? = null,
    val name: String? = null,
    @SerialName("merchant_name") val merchantName: String? = null,
    val pending: Boolean = false,
    @SerialName("personal_finance_category") val personalFinanceCategory: PersonalFinanceCategory? = null,
)

@Serializable
data class RemovedTransaction(@SerialName("transaction_id") val transactionId: String)

@Serializable
data class TransactionsSyncResponse(
    val added: List<PlaidTransaction> = emptyList(),
    val modified: List<PlaidTransaction> = emptyList(),
    val removed: List<RemovedTransaction> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("transactions_update_status") val updateStatus: String? = null,
)

@Serializable
data class PlaidApr(
    @SerialName("apr_percentage") val aprPercentage: Double? = null,
    @SerialName("apr_type") val aprType: String? = null,
)

@Serializable
data class CreditLiability(
    @SerialName("account_id") val accountId: String? = null,
    val aprs: List<PlaidApr> = emptyList(),
    @SerialName("minimum_payment_amount") val minimumPaymentAmount: Double? = null,
    @SerialName("next_payment_due_date") val nextPaymentDueDate: String? = null,
)

@Serializable
data class StudentLiability(
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("interest_rate_percentage") val interestRatePercentage: Double? = null,
    @SerialName("minimum_payment_amount") val minimumPaymentAmount: Double? = null,
    @SerialName("next_payment_due_date") val nextPaymentDueDate: String? = null,
    @SerialName("loan_name") val loanName: String? = null,
)

@Serializable
data class MortgageInterestRate(val percentage: Double? = null)

@Serializable
data class MortgageLiability(
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("interest_rate") val interestRate: MortgageInterestRate? = null,
    @SerialName("next_monthly_payment") val nextMonthlyPayment: Double? = null,
    @SerialName("next_payment_due_date") val nextPaymentDueDate: String? = null,
)

@Serializable
data class Liabilities(
    val credit: List<CreditLiability>? = null,
    val student: List<StudentLiability>? = null,
    val mortgage: List<MortgageLiability>? = null,
)

@Serializable
data class LiabilitiesGetResponse(val accounts: List<PlaidAccount> = emptyList(), val liabilities: Liabilities = Liabilities())

@Serializable
data class PlaidHolding(
    @SerialName("account_id") val accountId: String,
    @SerialName("security_id") val securityId: String,
    val quantity: Double = 0.0,
    @SerialName("institution_value") val institutionValue: Double = 0.0,
    @SerialName("cost_basis") val costBasis: Double? = null,
)

@Serializable
data class PlaidSecurity(
    @SerialName("security_id") val securityId: String,
    val name: String? = null,
    @SerialName("ticker_symbol") val tickerSymbol: String? = null,
)

@Serializable
data class HoldingsGetResponse(
    val accounts: List<PlaidAccount> = emptyList(),
    val holdings: List<PlaidHolding> = emptyList(),
    val securities: List<PlaidSecurity> = emptyList(),
)
