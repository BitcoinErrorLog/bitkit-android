package to.bitkit.ext

import java.time.Instant

fun ULong.toActivityItemDate(): String {
    return Instant.ofEpochSecond(this.toLong()).formatted(DatePattern.ACTIVITY_DATE)
}

fun ULong.toActivityItemTime(): String {
    return Instant.ofEpochSecond(this.toLong()).formatted(DatePattern.ACTIVITY_TIME)
}
