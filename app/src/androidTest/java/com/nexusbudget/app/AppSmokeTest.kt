package com.nexusbudget.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexusbudget.app.data.ImportFile
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Walks through onboarding with demo data and opens every main screen, failing if any screen
 * crashes or doesn't render. Saves a screenshot of each screen for review.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val screenshotDir: File by lazy {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        dir.apply { mkdirs() }
    }

    private fun waitFor(text: String, timeout: Long = 20_000) {
        try {
            rule.waitUntil(timeoutMillis = timeout) {
                rule.onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            screenshot("failure-${text.take(20).replace(' ', '-')}")
            // Kept on one line so it shows up in the Gradle console output.
            throw AssertionError("Timed out waiting for \"$text\". On screen: ${visibleText()}", e)
        }
    }

    /** Every text and content description currently on screen, joined into one line. */
    private fun visibleText(): String = runCatching {
        rule.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .flatMap(::texts)
            .joinToString(" | ")
            .take(4000)
    }.getOrElse { "(unavailable: ${it.message})" }

    private fun texts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.children.flatMap(::texts)

    /**
     * Fails if any on-screen text matching [texts] doesn't fit on one line in the space it's given, for
     * example a tab label cut down to "Transactio".
     */
    private fun assertNotCutOff(vararg texts: String) {
        val problems = texts.flatMap { text ->
            val nodes = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.isEmpty()) return@flatMap listOf("\"$text\" isn't on screen")
            nodes.mapNotNull { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                val layout = layouts.firstOrNull() ?: return@mapNotNull null
                // Don't use hasVisualOverflow: centered text reports overflow even when every letter shows.
                val needed = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                val clipped = layout.multiParagraph.didExceedMaxLines || layout.lineCount > 1 ||
                    (0 until layout.lineCount).any { layout.isLineEllipsized(it) } || needed > layout.size.width + 1
                if (!clipped) null else "\"$text\" needs ${needed}px but has ${layout.size.width}px (lines=${layout.lineCount})"
            }
        }
        assertTrue("Cut-off text: ${problems.joinToString(" | ")}", problems.isEmpty())
    }

    /** Fails unless the screen's sub-tabs are spread evenly, with the same space on the left and right. */
    private fun assertSubTabsCentered(count: Int) {
        val row = rule.onNodeWithTag("subtabs").fetchSemanticsNode().boundsInRoot
        val first = rule.onNodeWithTag("subtab-0").fetchSemanticsNode().boundsInRoot
        val last = rule.onNodeWithTag("subtab-${count - 1}").fetchSemanticsNode().boundsInRoot
        val left = first.left - row.left
        val right = row.right - last.right
        assertTrue("Tabs aren't centered: ${left}px on the left, ${right}px on the right", left >= 0f && abs(left - right) <= 2f)
    }

    /** Scrolls the screen's list to [text], retrying while the list is still filling in. */
    private fun scrollTo(text: String) {
        rule.waitUntil(timeoutMillis = 20_000) {
            runCatching { rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true)) }.isSuccess
        }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        runCatching {
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            File(screenshotDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun openTab(route: String, expected: String) {
        rule.onNodeWithTag("tab-$route").performClick()
        waitFor(expected)
    }

    @Test
    fun demoWalkthrough() {
        // Onboarding
        waitFor("Nexus Budget")
        screenshot("00-onboarding")
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        waitFor("Private by design")
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        waitFor("Explore with demo data")
        rule.onNodeWithText("Explore with demo data").performScrollTo().performClick()

        // Home
        waitFor("Safe to spend", timeout = 30_000)
        waitFor("Net worth")
        screenshot("01-home")
        scrollTo("Recent activity")
        screenshot("02-home-scrolled")

        // Accounts
        openTab("accounts", "Net worth")
        waitFor("Everyday Checking")
        screenshot("03-accounts")
        rule.onNodeWithText("Everyday Checking").performClick()
        waitFor("Balance")
        screenshot("04-account-detail")
        pressBack()
        waitFor("Everyday Checking")
        scrollTo("Federal Student Loan")
        rule.onNodeWithText("Federal Student Loan").performClick()
        waitFor("Debt details")
        screenshot("05-student-loan")
        pressBack()

        // Budget and its sub-tabs
        openTab("budget", "Left to budget")
        assertNotCutOff("Budget", "Transactions", "Recurring", "Trends", "Home", "Accounts", "Plans", "Ask AI")
        assertSubTabsCentered(count = 4)
        screenshot("06-budget")
        rule.onNodeWithText("Transactions").performClick()
        waitFor("Uncategorized (")
        screenshot("07-transactions")
        rule.onNodeWithText("Recurring").performClick()
        waitFor("Every month")
        screenshot("08-recurring")
        rule.onNodeWithText("Trends").performClick()
        waitFor("Income vs. spending")
        screenshot("09-trends")

        // Plans
        openTab("plans", "Recommendations")
        screenshot("10-plans")
        scrollTo("Debt-free by")
        screenshot("11-debt-plan")
        scrollTo("Cash flow, next 30 days")
        screenshot("12-forecast")

        // Assistant (no API key yet)
        openTab("assistant", "Connect Claude")
        screenshot("13-assistant-setup")

        // Settings and add-account flow
        openTab("home", "Good ") // Home keeps its scroll position, so check the greeting in the top bar
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Connections")
        screenshot("14-settings")
        pressBack()
        openTab("accounts", "Add account") // The button floats above the list, which is still scrolled
        rule.onNodeWithContentDescription("Add account").performClick()
        waitFor("Import a statement file")
        screenshot("15-connect")

        // A downloaded statement file opened with the app goes straight to the import screen.
        val container = (rule.activity.application as NexusApp).container
        container.pendingImport.value = ImportFile("statement.qfx", SAMPLE_STATEMENT)
        waitFor("Creates a new account")
        screenshot("16-import-preview")
        rule.onNodeWithText("Import").performScrollTo().performClick()
        waitFor("Import complete")
        waitFor("2 new transactions")
        screenshot("17-import-done")
        rule.onNodeWithText("View accounts").performClick()
        waitFor("Add account")
        scrollTo("••8642")
        screenshot("18-imported-account")
    }

    private companion object {
        val SAMPLE_STATEMENT = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102

            <OFX>
            <SIGNONMSGSRSV1><SONRS><STATUS><CODE>0<SEVERITY>INFO</STATUS><FI><ORG>Test Credit Union<FID>42</FI></SONRS></SIGNONMSGSRSV1>
            <BANKMSGSRSV1><STMTTRNRS><TRNUID>1<STATUS><CODE>0<SEVERITY>INFO</STATUS>
            <STMTRS><CURDEF>USD
            <BANKACCTFROM><BANKID>123456789<ACCTID>9900008642<ACCTTYPE>CHECKING</BANKACCTFROM>
            <BANKTRANLIST><DTSTART>20260901<DTEND>20260920
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260915<TRNAMT>-42.10<FITID>T1<NAME>CORNER MARKET</STMTTRN>
            <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260916<TRNAMT>1500.00<FITID>T2<NAME>EMPLOYER DIRECT DEP</STMTTRN>
            </BANKTRANLIST>
            <LEDGERBAL><BALAMT>1234.56<DTASOF>20260920</LEDGERBAL>
            </STMTRS></STMTTRNRS></BANKMSGSRSV1>
            </OFX>
        """.trimIndent()
    }
}
