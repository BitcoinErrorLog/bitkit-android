package to.bitkit.helpers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

/**
 * Test helper for wallet operations in E2E tests
 */
object WalletTestHelper {

    // MARK: - Navigation

    /**
     * Navigate to the Paykit section
     */
    fun navigateToPaykit(rule: ComposeTestRule) {
        // Tap on Settings tab
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        
        // Tap on Paykit menu item
        rule.onNodeWithText("Paykit").performClick()
        rule.waitForIdle()
    }

    /**
     * Navigate to Contacts
     */
    fun navigateToContacts(rule: ComposeTestRule) {
        navigateToPaykit(rule)
        
        rule.onNodeWithText("Contacts").performClick()
        rule.waitForIdle()
    }

    /**
     * Navigate to Session Management
     */
    fun navigateToSessionManagement(rule: ComposeTestRule) {
        navigateToPaykit(rule)
        
        rule.onNodeWithText("Sessions").performClick()
        rule.waitForIdle()
    }

    // MARK: - Wallet State

    /**
     * Check if wallet is initialized
     */
    fun isWalletInitialized(rule: ComposeTestRule): Boolean {
        return try {
            rule.onNodeWithText("Balance").assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }
    }

    /**
     * Wait for wallet to be ready
     */
    fun waitForWalletReady(
        rule: ComposeTestRule,
        timeoutMs: Long = 30000
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                rule.onNodeWithText("Send").assertIsDisplayed()
                return true
            } catch (e: AssertionError) {
                Thread.sleep(500)
            }
        }
        return false
    }

    // MARK: - Session Verification

    /**
     * Check if a session is active
     */
    fun hasActiveSession(rule: ComposeTestRule): Boolean {
        navigateToSessionManagement(rule)
        
        return try {
            rule.onNodeWithText("No active sessions").assertDoesNotExist()
            true
        } catch (e: AssertionError) {
            false
        }
    }

    /**
     * Get the number of active sessions
     */
    fun getActiveSessionCount(rule: ComposeTestRule): Int {
        navigateToSessionManagement(rule)
        
        // Count session items
        var count = 0
        try {
            rule.onAllNodes(hasText("Session")).fetchSemanticsNodes().forEach { _ ->
                count++
            }
        } catch (e: Exception) {
            // No sessions found
        }
        return count
    }

    // MARK: - Contact Verification

    /**
     * Get the number of contacts
     */
    fun getContactCount(rule: ComposeTestRule): Int {
        navigateToContacts(rule)
        
        // Count contact items
        var count = 0
        try {
            rule.onAllNodes(hasText("Contact")).fetchSemanticsNodes().forEach { _ ->
                count++
            }
        } catch (e: Exception) {
            // No contacts found
        }
        return count
    }

    /**
     * Check if a specific contact exists
     */
    fun hasContact(rule: ComposeTestRule, name: String): Boolean {
        navigateToContacts(rule)
        
        return try {
            rule.onNodeWithText(name).assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }
    }

    // MARK: - Payment Flow

    /**
     * Initiate a payment
     */
    fun initiatePayment(
        rule: ComposeTestRule,
        amount: String,
        recipient: String
    ) {
        // Tap Send button
        rule.onNodeWithText("Send").performClick()
        rule.waitForIdle()
        
        // Enter amount
        rule.onNodeWithTag("AmountInput").performTextInput(amount)
        rule.waitForIdle()
        
        // Enter recipient
        rule.onNodeWithTag("RecipientInput").performTextInput(recipient)
        rule.waitForIdle()
    }

    // MARK: - UI Assertions

    /**
     * Assert that text is displayed
     */
    fun assertTextDisplayed(
        rule: ComposeTestRule,
        text: String,
        timeoutMs: Long = 5000
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                rule.onNodeWithText(text).assertIsDisplayed()
                return true
            } catch (e: AssertionError) {
                Thread.sleep(500)
            }
        }
        return false
    }

    /**
     * Assert that dialog is shown
     */
    fun assertDialogShown(
        rule: ComposeTestRule,
        title: String,
        timeoutMs: Long = 5000
    ): Boolean {
        return assertTextDisplayed(rule, title, timeoutMs)
    }

    /**
     * Dismiss dialogs by clicking OK or Cancel
     */
    fun dismissDialogs(rule: ComposeTestRule) {
        try {
            rule.onNodeWithText("OK").performClick()
        } catch (e: Exception) {
            try {
                rule.onNodeWithText("Cancel").performClick()
            } catch (e: Exception) {
                // No dialog to dismiss
            }
        }
        rule.waitForIdle()
    }
}
