package to.bitkit.ext

import java.time.Instant

fun ULong.toActivityItemDate(): String {
    return Instant.ofEpochSecond(this.toLong()).formatted(DatePattern.ACTIVITY_DATE)
}

fun ULong.toActivityItemTime(): String {
    return Instant.ofEpochSecond(this.toLong()).formatted(DatePattern.ACTIVITY_TIME)
}

/**
 * Safely subtracts [other] from this ULong, returning 0 if the result would be negative,
 * to prevent ULong wraparound by checking before subtracting, same as `x.saturating_sub(y)` in Rust.
 */
infix fun ULong.minusOrZero(other: ULong): ULong = if (this >= other) this - other else 0uL
