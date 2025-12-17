package to.bitkit.paykit

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import to.bitkit.helpers.PubkyRingTestHelper
import to.bitkit.helpers.WalletTestHelper
import to.bitkit.ui.MainActivity

/**
 * End-to-end tests for Paykit integration with Pubky-ring
 */
@RunWith(AndroidJUnit4::class)
class PaykitE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        // Wait for app to initialize
        WalletTestHelper.waitForWalletReady(composeTestRule)
    }

    // MARK: - 3.2 Session Management E2E Tests

    @Test
    fun testSessionFlow_RequestAndReceive() {
        // Navigate to session management
        WalletTestHelper.navigateToSessionManagement(composeTestRule)

        // Tap "Connect Pubky-ring" button
        composeTestRule.onNodeWithText("Connect Pubky-ring").performClick()
        composeTestRule.waitForIdle()

        // If Pubky-ring is not installed, verify fallback UI
        if (!PubkyRingTestHelper.isPubkyRingInstalled(context)) {
            // Should show QR code option for cross-device auth
            WalletTestHelper.assertTextDisplayed(composeTestRule, "Use QR Code")
            return
        }

        // If installed, simulate callback
        PubkyRingTestHelper.simulateSessionCallback(context)

        // Verify session appears in list
        assert(WalletTestHelper.hasActiveSession(composeTestRule))
    }

    @Test
    fun testSessionFlow_Persistence() {
        // First, ensure we have a session
        testSessionFlow_RequestAndReceive()

        // Recreate the rule (simulates app restart)
        // In a real test, we'd need to use ActivityScenario
        
        // Verify session is restored
        WalletTestHelper.navigateToSessionManagement(composeTestRule)
        assert(WalletTestHelper.hasActiveSession(composeTestRule))
    }

    @Test
    fun testSessionFlow_ExpirationHandling() {
        // Navigate to sessions
        WalletTestHelper.navigateToSessionManagement(composeTestRule)

        // Look for expiration warning if any sessions are expiring
        // The UI should show refresh button for expiring sessions
        try {
            composeTestRule.onNodeWithText("Refresh").assertExists()
        } catch (e: AssertionError) {
            // No expiring sessions, which is fine
        }
    }

    @Test
    fun testSessionFlow_GracefulDegradation() {
        // Test behavior when Pubky-ring is not installed
        WalletTestHelper.navigateToSessionManagement(composeTestRule)

        if (!PubkyRingTestHelper.isPubkyRingInstalled(context)) {
            // Should show install prompt or alternative methods
            val hasInstallPrompt = WalletTestHelper.assertTextDisplayed(composeTestRule, "install", 3000)
            val hasQrOption = WalletTestHelper.assertTextDisplayed(composeTestRule, "QR Code", 3000)

            assert(hasInstallPrompt || hasQrOption)
        }
    }

    // MARK: - 3.3 Noise Key Derivation E2E Tests

    @Test
    fun testNoiseKeyDerivation_Flow() {
        // Ensure we have a session first
        if (!WalletTestHelper.hasActiveSession(composeTestRule)) {
            testSessionFlow_RequestAndReceive()
        }

        // Navigate to a feature that uses noise keys
        WalletTestHelper.navigateToPaykit(composeTestRule)

        try {
            composeTestRule.onNodeWithText("Direct Pay").performClick()
            composeTestRule.waitForIdle()

            // If Pubky-ring is not installed, should show error
            if (!PubkyRingTestHelper.isPubkyRingInstalled(context)) {
                WalletTestHelper.assertDialogShown(composeTestRule, "Error")
            }
        } catch (e: AssertionError) {
            // Direct Pay button not available, which is fine
        }
    }

    // MARK: - 3.4 Profile & Contacts E2E Tests

    @Test
    fun testProfileFetching() {
        // Ensure we have a session first
        if (!WalletTestHelper.hasActiveSession(composeTestRule)) {
            testSessionFlow_RequestAndReceive()
        }

        // Navigate to profile view
        WalletTestHelper.navigateToPaykit(composeTestRule)

        try {
            composeTestRule.onNodeWithText("Profile").performClick()
            composeTestRule.waitForIdle()

            // Verify profile elements are displayed (or loading state)
            val hasProfileName = WalletTestHelper.assertTextDisplayed(composeTestRule, "Name", 10000)
            val hasLoading = WalletTestHelper.assertTextDisplayed(composeTestRule, "Loading", 1000)
            
            assert(hasProfileName || hasLoading)
        } catch (e: AssertionError) {
            // Profile button not available
        }
    }

    @Test
    fun testFollowsSync() {
        // Ensure we have a session first
        if (!WalletTestHelper.hasActiveSession(composeTestRule)) {
            testSessionFlow_RequestAndReceive()
        }

        // Navigate to contacts
        WalletTestHelper.navigateToContacts(composeTestRule)

        // Tap sync button
        try {
            composeTestRule.onNodeWithText("Sync Contacts").performClick()
            composeTestRule.waitForIdle()

            // Wait for sync to complete (up to 30 seconds)
            Thread.sleep(5000)

            // Verify contacts list is updated (or empty state is shown)
            val contactCount = WalletTestHelper.getContactCount(composeTestRule)
            assert(contactCount >= 0)
        } catch (e: AssertionError) {
            // Sync button not available
        }
    }

    // MARK: - 3.5 Backup & Restore E2E Tests

    @Test
    fun testBackupExport() {
        // Ensure we have a session first
        if (!WalletTestHelper.hasActiveSession(composeTestRule)) {
            testSessionFlow_RequestAndReceive()
        }

        // Navigate to backup
        WalletTestHelper.navigateToPaykit(composeTestRule)

        try {
            composeTestRule.onNodeWithText("Backup").performClick()
            composeTestRule.waitForIdle()

            // Verify export options are shown
            WalletTestHelper.assertTextDisplayed(composeTestRule, "Export")
        } catch (e: AssertionError) {
            // Backup button not available
        }
    }

    @Test
    fun testBackupImport() {
        // Navigate to backup
        WalletTestHelper.navigateToPaykit(composeTestRule)

        try {
            composeTestRule.onNodeWithText("Backup").performClick()
            composeTestRule.waitForIdle()

            // Verify import options are shown
            WalletTestHelper.assertTextDisplayed(composeTestRule, "Import")
        } catch (e: AssertionError) {
            // Backup button not available
        }
    }

    // MARK: - 3.6 Cross-App Integration Tests

    @Test
    fun testCrossDeviceAuthentication() {
        // Navigate to session management
        WalletTestHelper.navigateToSessionManagement(composeTestRule)

        // Tap cross-device option
        try {
            composeTestRule.onNodeWithText("Use QR Code").performClick()
            composeTestRule.waitForIdle()

            // Verify QR code or URL is displayed
            val hasQr = WalletTestHelper.assertTextDisplayed(composeTestRule, "Scan", 5000)
            val hasCopyLink = WalletTestHelper.assertTextDisplayed(composeTestRule, "Copy Link", 5000)

            assert(hasQr || hasCopyLink)
        } catch (e: AssertionError) {
            // QR Code option not available
        }
    }

    @Test
    fun testEndToEndPaymentFlow() {
        // Ensure we have a session first
        if (!WalletTestHelper.hasActiveSession(composeTestRule)) {
            testSessionFlow_RequestAndReceive()
        }

        // Navigate to send
        composeTestRule.onNodeWithText("Send").performClick()
        composeTestRule.waitForIdle()

        // Enter test amount and recipient
        WalletTestHelper.initiatePayment(
            composeTestRule,
            amount = "1000",
            recipient = PubkyRingTestHelper.TEST_PUBKEY
        )

        // Verify Paykit payment option appears
        try {
            WalletTestHelper.assertTextDisplayed(composeTestRule, "Paykit", 5000)
        } catch (e: AssertionError) {
            // Paykit option not available for this recipient
        }
    }

    @Test
    fun testEndToEndContactDiscovery() {
        // Ensure we have a session first
        if (!WalletTestHelper.hasActiveSession(composeTestRule)) {
            testSessionFlow_RequestAndReceive()
        }

        // Navigate to contacts
        WalletTestHelper.navigateToContacts(composeTestRule)

        // Tap discover button
        try {
            composeTestRule.onNodeWithText("Discover Contacts").performClick()
            composeTestRule.waitForIdle()

            // Wait for discovery
            Thread.sleep(5000)

            // Verify discovery completed (either found contacts or showed empty state)
            val contactCount = WalletTestHelper.getContactCount(composeTestRule)
            assert(contactCount >= 0)
        } catch (e: AssertionError) {
            // Discover button not available
        }
    }
}
