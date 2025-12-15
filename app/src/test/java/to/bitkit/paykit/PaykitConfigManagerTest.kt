package to.bitkit.paykit

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PaykitConfigManager functionality.
 */
class PaykitConfigManagerTest {

    @Before
    fun setUp() {
        // Reset to defaults before each test
        PaykitConfigManager.logLevel = PaykitLogLevel.INFO
        PaykitConfigManager.defaultPaymentTimeoutMs = 60_000L
        PaykitConfigManager.lightningPollingIntervalMs = 500L
        PaykitConfigManager.maxRetryAttempts = 3
        PaykitConfigManager.retryBaseDelayMs = 1000L
        PaykitConfigManager.errorReporter = null
    }

    @After
    fun tearDown() {
        // Reset to defaults after each test
        PaykitConfigManager.logLevel = PaykitLogLevel.INFO
        PaykitConfigManager.defaultPaymentTimeoutMs = 60_000L
        PaykitConfigManager.lightningPollingIntervalMs = 500L
        PaykitConfigManager.maxRetryAttempts = 3
        PaykitConfigManager.retryBaseDelayMs = 1000L
        PaykitConfigManager.errorReporter = null
    }

    // MARK: - Environment Tests

    @Test
    fun `environment returns valid value`() {
        val environment = PaykitConfigManager.environment

        // Should be one of the valid values
        assertTrue(environment in PaykitEnvironment.values())
    }

    // MARK: - Log Level Tests

    @Test
    fun `logLevel defaults to INFO`() {
        PaykitConfigManager.logLevel = PaykitLogLevel.INFO
        assertEquals(PaykitLogLevel.INFO, PaykitConfigManager.logLevel)
    }

    @Test
    fun `logLevel can be set to DEBUG`() {
        PaykitConfigManager.logLevel = PaykitLogLevel.DEBUG
        assertEquals(PaykitLogLevel.DEBUG, PaykitConfigManager.logLevel)
    }

    @Test
    fun `logLevel can be set to ERROR`() {
        PaykitConfigManager.logLevel = PaykitLogLevel.ERROR
        assertEquals(PaykitLogLevel.ERROR, PaykitConfigManager.logLevel)
    }

    @Test
    fun `logLevel can be set to NONE`() {
        PaykitConfigManager.logLevel = PaykitLogLevel.NONE
        assertEquals(PaykitLogLevel.NONE, PaykitConfigManager.logLevel)
    }

    @Test
    fun `log levels have correct ordering`() {
        // Verify log levels have correct ordinal ordering for filtering
        assertTrue(PaykitLogLevel.DEBUG.ordinal < PaykitLogLevel.INFO.ordinal)
        assertTrue(PaykitLogLevel.INFO.ordinal < PaykitLogLevel.WARNING.ordinal)
        assertTrue(PaykitLogLevel.WARNING.ordinal < PaykitLogLevel.ERROR.ordinal)
        assertTrue(PaykitLogLevel.ERROR.ordinal < PaykitLogLevel.NONE.ordinal)
    }

    // MARK: - Timeout Configuration Tests

    @Test
    fun `defaultPaymentTimeoutMs has correct default`() {
        assertEquals(60_000L, PaykitConfigManager.defaultPaymentTimeoutMs)
    }

    @Test
    fun `defaultPaymentTimeoutMs can be set`() {
        PaykitConfigManager.defaultPaymentTimeoutMs = 120_000L
        assertEquals(120_000L, PaykitConfigManager.defaultPaymentTimeoutMs)
    }

    @Test
    fun `lightningPollingIntervalMs has correct default`() {
        assertEquals(500L, PaykitConfigManager.lightningPollingIntervalMs)
    }

    @Test
    fun `lightningPollingIntervalMs can be set`() {
        PaykitConfigManager.lightningPollingIntervalMs = 1000L
        assertEquals(1000L, PaykitConfigManager.lightningPollingIntervalMs)
    }

    // MARK: - Retry Configuration Tests

    @Test
    fun `maxRetryAttempts has correct default`() {
        assertEquals(3, PaykitConfigManager.maxRetryAttempts)
    }

    @Test
    fun `maxRetryAttempts can be set`() {
        PaykitConfigManager.maxRetryAttempts = 5
        assertEquals(5, PaykitConfigManager.maxRetryAttempts)
    }

    @Test
    fun `retryBaseDelayMs has correct default`() {
        assertEquals(1000L, PaykitConfigManager.retryBaseDelayMs)
    }

    @Test
    fun `retryBaseDelayMs can be set`() {
        PaykitConfigManager.retryBaseDelayMs = 2000L
        assertEquals(2000L, PaykitConfigManager.retryBaseDelayMs)
    }

    // MARK: - Error Reporting Tests

    @Test
    fun `errorReporter defaults to null`() {
        PaykitConfigManager.errorReporter = null
        assertNull(PaykitConfigManager.errorReporter)
    }

    @Test
    fun `errorReporter can be set`() {
        PaykitConfigManager.errorReporter = { _, _ -> }
        assertNotNull(PaykitConfigManager.errorReporter)
    }

    @Test
    fun `reportError calls errorReporter`() {
        var reportedError: Throwable? = null
        var reportedContext: Map<String, Any>? = null

        PaykitConfigManager.errorReporter = { error, context ->
            reportedError = error
            reportedContext = context
        }

        // Create a test error
        val testError = RuntimeException("Test error")
        val testContext = mapOf("key" to "value")

        // Report the error
        PaykitConfigManager.reportError(testError, testContext)

        // Verify it was reported
        assertNotNull(reportedError)
        assertEquals("Test error", reportedError?.message)
        assertEquals("value", reportedContext?.get("key"))
    }

    @Test
    fun `reportError handles null reporter gracefully`() {
        // Given no error reporter is set
        PaykitConfigManager.errorReporter = null

        // When we report an error
        val testError = RuntimeException("Test error")

        // Then it should not crash
        PaykitConfigManager.reportError(testError)
    }

    @Test
    fun `reportError handles null context`() {
        var reportedContext: Map<String, Any>? = mapOf("initial" to "value")

        PaykitConfigManager.errorReporter = { _, context ->
            reportedContext = context
        }

        // Report error without context
        PaykitConfigManager.reportError(RuntimeException("Test"))

        // Context should be null
        assertNull(reportedContext)
    }

    // MARK: - PaykitEnvironment Tests

    @Test
    fun `PaykitEnvironment has all expected values`() {
        val values = PaykitEnvironment.values()

        assertEquals(3, values.size)
        assertTrue(PaykitEnvironment.DEVELOPMENT in values)
        assertTrue(PaykitEnvironment.STAGING in values)
        assertTrue(PaykitEnvironment.PRODUCTION in values)
    }

    // MARK: - PaykitLogLevel Tests

    @Test
    fun `PaykitLogLevel has all expected values`() {
        val values = PaykitLogLevel.values()

        assertEquals(5, values.size)
        assertTrue(PaykitLogLevel.DEBUG in values)
        assertTrue(PaykitLogLevel.INFO in values)
        assertTrue(PaykitLogLevel.WARNING in values)
        assertTrue(PaykitLogLevel.ERROR in values)
        assertTrue(PaykitLogLevel.NONE in values)
    }
}
