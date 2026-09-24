package com.nexusbudget.core.model

import com.nexusbudget.core.engine.MerchantNormalizer
import java.time.LocalDate

data class Transaction(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    /** Negative when money leaves the account, positive when money comes in. */
    val amount: Long,
    val description: String,
    val merchant: String = MerchantNormalizer.displayName(description),
    val categoryId: String? = null,
    val pending: Boolean = false,
    val notes: String? = null,
    /** Hidden from budgets and reports (for example a reimbursed work expense). */
    val excluded: Boolean = false,
    /** True once the user has confirmed or changed the category. */
    val userCategorized: Boolean = false,
) {
    val isOutflow: Boolean get() = amount < 0
    val merchantKey: String get() = MerchantNormalizer.key(merchant)
}
