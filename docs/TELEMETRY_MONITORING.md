# Telemetry and Monitoring Guide (Android)

This guide covers structured logging, metrics collection, and crash reporting for Paykit integration on Android.

## Table of Contents

1. [Logging Architecture](#logging-architecture)
2. [Android Logging Implementation](#android-logging-implementation)
3. [Crashlytics Integration](#crashlytics-integration)
4. [Metrics Collection](#metrics-collection)
5. [Privacy Considerations](#privacy-considerations)

---

## Logging Architecture

### Log Levels

| Level | Usage | Example |
|-------|-------|---------|
| Error | Unexpected failures requiring attention | Payment failed, session expired |
| Warning | Recoverable issues | Rate limited, fallback used |
| Info | Key operations | Session created, payment completed |
| Debug | Detailed flow information | Handshake steps, cache hits |

### Sensitive Data Handling

**NEVER LOG:**
- Private keys
- Session secrets
- Full payment addresses
- User identifiers (without hashing)
- Biometric data

**ALLOWED (with care):**
- Truncated public keys (first 8 chars)
- Hashed session IDs
- Payment amounts (aggregated, not individual)
- Error codes and stack traces

---

## Android Logging Implementation

### PaykitLogger Usage

```kotlin
// Standard logging
PaykitLogger.info("Session created", category = "session", context = mapOf(
    "ttl_seconds" to ttlSeconds,
    "capabilities" to capabilities.joinToString(",")
))

// Error logging with exception
PaykitLogger.error(
    "Payment failed",
    category = "payment",
    error = exception,
    context = mapOf("method" to methodId)
)

// Payment flow logging
PaykitLogger.logPaymentFlow(
    event = "initiated",
    paymentMethod = "lightning",
    amount = amountSats,
    durationMs = null
)

// Performance logging
PaykitLogger.logPerformance(
    operation = "handshake",
    durationMs = handshakeDuration,
    success = true
)
```

### Log Categories

| Category | Purpose |
|----------|---------|
| `session` | Session lifecycle events |
| `payment` | Payment initiation, completion, failure |
| `noise` | Noise protocol handshakes and encryption |
| `directory` | Contact discovery operations |
| `sync` | Profile/endpoint sync events |
| `storage` | Local storage operations |

---

## Crashlytics Integration

### Setup

Crashlytics is automatically configured via Firebase. Ensure:

```kotlin
// In Application class
FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
```

### Error Reporting

Errors are automatically reported via `PaykitConfigManager.reportError()`:

```kotlin
// Automatically called by PaykitLogger.error()
fun reportError(error: Throwable, context: Map<String, Any>?) {
    if (enableCrashReporting) {
        context?.forEach { (key, value) ->
            FirebaseCrashlytics.getInstance().setCustomKey(key, value.toString())
        }
        FirebaseCrashlytics.getInstance().recordException(error)
    }
}
```

### Custom Keys for Debugging

Set useful context for crash debugging:

```kotlin
FirebaseCrashlytics.getInstance().apply {
    setCustomKey("paykit_session_active", isSessionActive)
    setCustomKey("paykit_version", PAYKIT_VERSION)
    setCustomKey("last_operation", lastOperation)
}
```

---

## Metrics Collection

### Key Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `paykit_handshake_duration_ms` | Histogram | Time to complete Noise handshake |
| `paykit_payment_duration_ms` | Histogram | End-to-end payment time |
| `paykit_session_ttl_remaining_s` | Gauge | Session time-to-live remaining |
| `paykit_directory_contacts_count` | Gauge | Number of discovered contacts |
| `paykit_error_rate` | Counter | Errors per operation type |

### Implementation

```kotlin
object PaykitMetrics {
    private val handshakeDurations = mutableListOf<Long>()
    private val paymentDurations = mutableListOf<Long>()
    
    fun recordHandshakeDuration(durationMs: Long) {
        handshakeDurations.add(durationMs)
        Analytics.logEvent("paykit_handshake_completed", bundleOf(
            "duration_bucket" to durationBucket(durationMs)
        ))
    }
    
    fun recordPaymentDuration(durationMs: Long, success: Boolean) {
        if (success) paymentDurations.add(durationMs)
        Analytics.logEvent(
            if (success) "paykit_payment_completed" else "paykit_payment_failed",
            bundleOf("duration_bucket" to durationBucket(durationMs))
        )
    }
    
    private fun durationBucket(ms: Long): String = when {
        ms < 100 -> "<100ms"
        ms < 500 -> "100-500ms"
        ms < 1000 -> "500ms-1s"
        ms < 5000 -> "1-5s"
        else -> ">5s"
    }
}
```

---

## Privacy Considerations

### Data Retention

- Logs are retained for 7 days in debug builds
- Production builds retain aggregated metrics only
- No personal data is sent to analytics

### User Consent

```kotlin
fun updateAnalyticsConsent(consent: Boolean) {
    PaykitConfigManager.analyticsEnabled = consent
    FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(consent)
    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(consent)
}
```

### Anonymization

All logged data must be anonymized:

```kotlin
fun anonymizePublicKey(pubkey: String): String {
    return pubkey.take(8) + "..."
}

fun anonymizePaymentHash(hash: String): String {
    return "hash_" + hash.take(6)
}
```

---

## Dashboard Queries

### Error Rate by Operation

```sql
SELECT 
  operation,
  COUNT(*) as error_count,
  COUNT(*) * 100.0 / SUM(COUNT(*)) OVER () as error_rate
FROM paykit_errors
WHERE timestamp > NOW() - INTERVAL '24 hours'
GROUP BY operation
ORDER BY error_count DESC
```

### Performance Percentiles

```sql
SELECT 
  operation,
  PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY duration_ms) as p50,
  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) as p95,
  PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) as p99
FROM paykit_performance
WHERE timestamp > NOW() - INTERVAL '24 hours'
GROUP BY operation
```

---

## Alerts

Configure alerts for:

1. **Error Rate Spike**: > 5% errors in 5 minute window
2. **Slow Handshakes**: P95 > 2 seconds
3. **Session Failures**: > 3 session creation failures per user per hour
4. **Payment Failures**: > 10% payment failure rate

See `PAYKIT_RELEASE_CHECKLIST.md` for production alert configuration.

