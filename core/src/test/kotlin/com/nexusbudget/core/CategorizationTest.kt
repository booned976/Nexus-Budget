package com.nexusbudget.core

import com.nexusbudget.core.engine.Categorizer
import com.nexusbudget.core.engine.MerchantNormalizer
import com.nexusbudget.core.engine.TransactionPipeline
import com.nexusbudget.core.model.Account
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Categories
import com.nexusbudget.core.model.CategoryRule
import com.nexusbudget.core.model.Transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CategorizationTest {

    private val day = LocalDate.of(2026, 3, 10)

    private fun txn(id: String, description: String, amount: Long, account: String = "chk", userCategory: String? = null) =
        Transaction(id, account, day, amount, description, categoryId = userCategory, userCategorized = userCategory != null)

    @Test
    fun `merchant names are cleaned`() {
        assertEquals("Greenleaf Grocery", MerchantNormalizer.displayName("POS DEBIT 0412 GREENLEAF GROCERY #1234 SPRINGFIELD IL"))
        assertEquals("Corner Bistro", MerchantNormalizer.displayName("SQ *CORNER BISTRO"))
        assertEquals("Quickfuel Gas Station", MerchantNormalizer.displayName("PURCHASE AUTHORIZED ON 03/02 QUICKFUEL GAS STATION 88213"))
        assertEquals(MerchantNormalizer.key("Corner Bistro"), MerchantNormalizer.key("CORNER BISTRO"))
    }

    @Test
    fun `keyword rules pick sensible categories`() {
        val categorizer = Categorizer()
        assertEquals(Categories.GROCERIES, categorizer.categorize(txn("1", "GREENLEAF GROCERY", -45_00)))
        assertEquals(Categories.PAYCHECK, categorizer.categorize(txn("2", "ACME CORP PAYROLL", 2_000_00)))
        assertEquals(Categories.LOAN_PAYMENT, categorizer.categorize(txn("3", "DEPT OF ED STUDENT LOAN PMT", -235_00)))
        assertEquals(Categories.CREDIT_CARD_PAYMENT, categorizer.categorize(txn("4", "PAYMENT THANK YOU", 300_00)))
        assertEquals(Categories.INTERNET_PHONE, categorizer.categorize(txn("5", "METRO MOBILE WIRELESS", -55_00)))
        assertEquals(Categories.UNCATEGORIZED, categorizer.categorize(txn("6", "XYZZY 123", -10_00)))
        assertEquals(Categories.OTHER_INCOME, categorizer.categorize(txn("7", "XYZZY 123", 10_00)))
        // "GAS" must match as a word, not inside another word.
        assertEquals(Categories.UNCATEGORIZED, categorizer.categorize(txn("8", "VEGASTRIP", -10_00)))
    }

    @Test
    fun `user rules and history beat keyword rules`() {
        val history = listOf(txn("h1", "CORNER BISTRO", -20_00, userCategory = Categories.GROCERIES))
        val rules = listOf(CategoryRule("r1", "BEAN THERE", Categories.RESTAURANTS))
        val categorizer = Categorizer(rules, history)
        assertEquals(Categories.GROCERIES, categorizer.categorize(txn("1", "CORNER BISTRO", -18_00)))
        assertEquals(Categories.RESTAURANTS, categorizer.categorize(txn("2", "BEAN THERE COFFEE", -5_00)))
    }

    @Test
    fun `transfers between own accounts are linked`() {
        val accounts = listOf(
            Account("chk", "Checking", AccountType.CHECKING, 0),
            Account("sav", "Savings", AccountType.SAVINGS, 0),
            Account("loan", "Student loan", AccountType.STUDENT_LOAN, 0),
        )
        val incoming = listOf(
            txn("a", "ONLINE TRANSFER TO SAV", -200_00, "chk"),
            txn("b", "TRANSFER FROM CHK", 200_00, "sav"),
            txn("c", "DEPT OF ED STUDENT LOAN PMT", -235_00, "chk"),
            txn("d", "PAYMENT RECEIVED", 235_00, "loan"),
            txn("e", "GREENLEAF GROCERY", -60_00, "chk"),
        )
        val result = TransactionPipeline.process(incoming, emptyList(), accounts, Categorizer()).transactions.associateBy { it.id }
        assertEquals(Categories.SAVINGS_TRANSFER, result.getValue("a").categoryId)
        assertEquals(Categories.SAVINGS_TRANSFER, result.getValue("b").categoryId)
        assertEquals(Categories.LOAN_PAYMENT, result.getValue("c").categoryId)
        assertEquals(Categories.TRANSFER, result.getValue("d").categoryId)
        assertEquals(Categories.GROCERIES, result.getValue("e").categoryId)
    }

    @Test
    fun `user categorized transactions are never changed`() {
        val accounts = listOf(Account("chk", "Checking", AccountType.CHECKING, 0))
        val incoming = listOf(txn("a", "GREENLEAF GROCERY", -60_00, userCategory = Categories.GIFTS))
        val result = TransactionPipeline.process(incoming, emptyList(), accounts, Categorizer()).transactions
        assertEquals(Categories.GIFTS, result.single().categoryId)
    }
}
