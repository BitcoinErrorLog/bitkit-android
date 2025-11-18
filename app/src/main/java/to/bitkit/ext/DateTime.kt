@file:Suppress("TooManyFunctions")

package to.bitkit.ext

import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

fun nowMillis(clock: Clock = Clock.System): Long = clock.now().toEpochMilliseconds()

fun nowTimestamp(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

fun Instant.formatted(pattern: String = DatePattern.DATE_TIME): String {
    val dateTime = LocalDateTime.ofInstant(this, ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return dateTime.format(formatter)
}

fun Long.toTimeUTC(): String {
    val instant = Instant.ofEpochMilli(this)
    val dateTime = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))
    return dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}

fun Long.toDateUTC(): String {
    val instant = Instant.ofEpochMilli(this)
    val dateTime = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))
    return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

fun Long.toLocalizedTimestamp(): String {
    val uLocale = ULocale.forLocale(Locale.US)
    val formatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, uLocale)
        ?: return SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US).format(Date(this))
    return formatter.format(Date(this))
}

@Suppress("LongMethod")
fun Long.toRelativeTimeString(
    locale: Locale = Locale.getDefault(),
    clock: Clock = Clock.System,
): String {
    val now = nowMillis(clock)
    val diffMillis = now - this

    val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale),
        null,
        RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,
    ) ?: return toLocalizedTimestamp()

    val seconds = diffMillis / Constants.MILLIS_TO_SECONDS
    val minutes = (seconds / Constants.SECONDS_TO_MINUTES).toInt()
    val hours = (minutes / Constants.MINUTES_TO_HOURS).toInt()
    val days = (hours / Constants.HOURS_TO_DAYS).toInt()
    val weeks = (days / Constants.DAYS_TO_WEEKS).toInt()
    val months = (days / Constants.DAYS_TO_MONTHS).toInt()
    val years = (days / Constants.DAYS_TO_YEARS).toInt()

    return when {
        seconds < Constants.SECONDS_THRESHOLD -> formatter.format(
            RelativeDateTimeFormatter.Direction.PLAIN,
            RelativeDateTimeFormatter.AbsoluteUnit.NOW,
        )

        minutes < Constants.MINUTES_THRESHOLD -> formatter.format(
            minutes.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.MINUTES,
        )

        hours < Constants.HOURS_THRESHOLD -> formatter.format(
            hours.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.HOURS,
        )

        days < Constants.YESTERDAY_THRESHOLD -> formatter.format(
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.AbsoluteUnit.DAY,
        )

        days < Constants.DAYS_THRESHOLD -> formatter.format(
            days.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.DAYS,
        )

        weeks < Constants.WEEKS_THRESHOLD -> formatter.format(
            weeks.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.WEEKS,
        )

        months < Constants.MONTHS_THRESHOLD -> formatter.format(
            months.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.MONTHS,
        )

        else -> formatter.format(
            years.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.YEARS,
        )
    }
}

fun getDaysInMonth(month: LocalDate): List<LocalDate?> {
    val firstDayOfMonth = LocalDate(month.year, month.month, Constants.FIRST_DAY_OF_MONTH)
    val daysInMonth = month.month.length(isLeapYear(month.year))

    // Get the day of week for the first day (1 = Monday, 7 = Sunday)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal + CalendarConstants.CALENDAR_WEEK_OFFSET

    // Calculate offset (days before the first day)
    // We want Sunday to be 0, so adjust accordingly
    val offset = (firstDayOfWeek % CalendarConstants.DAYS_IN_WEEK_MOD)

    val days = mutableListOf<LocalDate?>()

    // Add empty spaces before the first day
    repeat(offset) {
        days.add(null)
    }

    // Add all days of the month
    for (day in Constants.FIRST_DAY_OF_MONTH..daysInMonth) {
        days.add(LocalDate(month.year, month.month, day))
    }

    // Add empty spaces to complete the last week (total should be multiple of 7)
    while (days.size % CalendarConstants.DAYS_IN_WEEK_MOD != 0) {
        days.add(null)
    }

    return days
}

fun isLeapYear(year: Int): Boolean {
    return (year % Constants.LEAP_YEAR_DIVISOR_4 == 0 && year % Constants.LEAP_YEAR_DIVISOR_100 != 0) || (year % Constants.LEAP_YEAR_DIVISOR_400 == 0)
}

fun isDateInRange(dateMillis: Long, startMillis: Long?, endMillis: Long?): Boolean {
    if (startMillis == null) return false
    val end = endMillis ?: startMillis

    val normalizedDate =
        kotlinx.datetime.Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val normalizedStart = kotlinx.datetime.Instant.fromEpochMilliseconds(startMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val normalizedEnd =
        kotlinx.datetime.Instant.fromEpochMilliseconds(end).toLocalDateTime(TimeZone.currentSystemDefault()).date

    return normalizedDate >= normalizedStart && normalizedDate <= normalizedEnd
}

fun LocalDate.toMonthYearString(): String {
    val formatter = SimpleDateFormat(DatePattern.MONTH_YEAR_FORMAT, Locale.getDefault())
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, Constants.FIRST_DAY_OF_MONTH)
    return formatter.format(calendar.time)
}

fun LocalDate.minusMonths(months: Int): LocalDate =
    this.toJavaLocalDate().minusMonths(months.toLong()).withDayOfMonth(1) // Always use first day of month for display
        .toKotlinLocalDate()

fun LocalDate.plusMonths(months: Int): LocalDate =
    this.toJavaLocalDate().plusMonths(months.toLong()).withDayOfMonth(1) // Always use first day of month for display
        .toKotlinLocalDate()

fun LocalDate.endOfDay(): Long {
    return this.atStartOfDayIn(TimeZone.currentSystemDefault()).plus(1.days).minus(1.milliseconds).toEpochMilliseconds()
}

fun utcDateFormatterOf(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
    timeZone = java.util.TimeZone.getTimeZone("UTC")
}

object DatePattern {
    const val DATE_TIME = "dd/MM/yyyy, HH:mm"
    const val INVOICE_EXPIRY = "MMM dd, h:mm a"
    const val ACTIVITY_DATE = "MMMM d"
    const val ACTIVITY_ROW_DATE = "MMMM d, HH:mm"
    const val ACTIVITY_ROW_DATE_YEAR = "MMMM d yyyy, HH:mm"
    const val ACTIVITY_TIME = "h:mm"
    const val CHANNEL_DETAILS = "MMM d, yyyy, HH:mm"
    const val LOG_FILE = "yyyy-MM-dd_HH-mm-ss"
    const val LOG_LINE = "yyyy-MM-dd HH:mm:ss.SSS"

    const val MONTH_YEAR_FORMAT = "MMMM yyyy"
    const val DATE_FORMAT = "MMM d, yyyy"
    const val WEEKDAY_FORMAT = "EEE"
}

private object Constants {
    // Time conversion factors
    const val MILLIS_TO_SECONDS = 1000.0
    const val SECONDS_TO_MINUTES = 60.0
    const val MINUTES_TO_HOURS = 60.0
    const val HOURS_TO_DAYS = 24.0
    const val DAYS_TO_WEEKS = 7.0
    const val DAYS_TO_MONTHS = 30.0
    const val DAYS_TO_YEARS = 365.0

    // Time unit thresholds
    const val SECONDS_THRESHOLD = 60
    const val MINUTES_THRESHOLD = 60
    const val HOURS_THRESHOLD = 24
    const val YESTERDAY_THRESHOLD = 2
    const val DAYS_THRESHOLD = 7
    const val WEEKS_THRESHOLD = 4
    const val MONTHS_THRESHOLD = 12

    // Calendar
    const val FIRST_DAY_OF_MONTH = 1

    // Leap year calculation
    const val LEAP_YEAR_DIVISOR_4 = 4
    const val LEAP_YEAR_DIVISOR_100 = 100
    const val LEAP_YEAR_DIVISOR_400 = 400
}

object CalendarConstants {
    // Calendar grid
    const val DAYS_IN_WEEK = 7

    // Date formatting
    const val WEEKDAY_ABBREVIATION_LENGTH = 3

    // Calendar math
    const val DAYS_IN_WEEK_MOD = 7
    const val CALENDAR_WEEK_OFFSET = 1
    const val MONTH_INDEX_OFFSET = 1
}
