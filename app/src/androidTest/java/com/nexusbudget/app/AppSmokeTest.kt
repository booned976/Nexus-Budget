package com.nexusbudget.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
        rule.waitUntil(timeoutMillis = timeout) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
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
        rule.onNodeWithText("Continue").performClick()
        waitFor("Private by design")
        rule.onNodeWithText("Continue").performClick()
        waitFor("Explore with demo data")
        rule.onNodeWithText("Explore with demo data").performClick()

        // Home
        waitFor("Safe to spend", timeout = 30_000)
        waitFor("Net worth")
        screenshot("01-home")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Recent activity"))
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
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Federal Student Loan"))
        rule.onNodeWithText("Federal Student Loan").performClick()
        waitFor("Debt details")
        screenshot("05-student-loan")
        pressBack()

        // Budget and its sub-tabs
        openTab("budget", "Left to budget")
        screenshot("06-budget")
        rule.onNodeWithText("Transactions").performClick()
        waitFor("Search merchants")
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
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Debt-free by"))
        screenshot("11-debt-plan")
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Cash flow, next 30 days"))
        screenshot("12-forecast")

        // Assistant (no API key yet)
        openTab("assistant", "Connect Claude")
        screenshot("13-assistant-setup")

        // Settings and add-account flow
        openTab("home", "Safe to spend")
        rule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Connections")
        screenshot("14-settings")
        pressBack()
        openTab("accounts", "Net worth")
        rule.onNodeWithText("Add account").performClick()
        waitFor("Connect with SimpleFIN")
        screenshot("15-connect")
    }
}
