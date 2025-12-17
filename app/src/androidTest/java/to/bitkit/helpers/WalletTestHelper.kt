package to.bitkit.helpers

import androidx.compose.ui.test.assertIsDisplayed
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
        
        // Check if "No active sessions" text is NOT displayed
        return try {
            rule.onNodeWithText("No active sessions").assertIsDisplayed()
            // If we get here, the "no sessions" text is displayed, so no active sessions
            false
        } catch (e: AssertionError) {
            // "No active sessions" not displayed means we have sessions
            true
        }
    }

    /**
     * Get the number of active sessions (simplified - returns 0 or 1+)
     */
    fun getActiveSessionCount(rule: ComposeTestRule): Int {
        return if (hasActiveSession(rule)) 1 else 0
    }

    // MARK: - Contact Verification

    /**
     * Get the number of contacts (simplified - returns 0 or 1+)
     */
    fun getContactCount(rule: ComposeTestRule): Int {
        navigateToContacts(rule)
        
        // Check if we have any contacts by looking for contact-related UI
        return try {
            rule.onNodeWithText("No contacts").assertIsDisplayed()
            0
        } catch (e: AssertionError) {
            // "No contacts" not displayed means we have contacts
            1
        }
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
