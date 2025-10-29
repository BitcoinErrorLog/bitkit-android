package to.bitkit.ext

import android.icu.text.DateFormat
import android.icu.util.ULocale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
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
    return formatter.format(Date(this))
}

fun getDaysInMonth(month: LocalDate): List<LocalDate?> {
    val firstDayOfMonth = LocalDate(month.year, month.month, CalendarConstants.FIRST_DAY_OF_MONTH)
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
    for (day in CalendarConstants.FIRST_DAY_OF_MONTH..daysInMonth) {
        days.add(LocalDate(month.year, month.month, day))
    }

    // Add empty spaces to complete the last week (total should be multiple of 7)
    while (days.size % CalendarConstants.DAYS_IN_WEEK_MOD != 0) {
        days.add(null)
    }

    return days
}

fun isLeapYear(year: Int): Boolean {
    return (year % CalendarConstants.LEAP_YEAR_DIVISOR_4 == 0 && year % CalendarConstants.LEAP_YEAR_DIVISOR_100 != 0) ||
        (year % CalendarConstants.LEAP_YEAR_DIVISOR_400 == 0)
}

fun isDateInRange(dateMillis: Long, startMillis: Long?, endMillis: Long?): Boolean {
    if (startMillis == null) return false
    val end = endMillis ?: startMillis

    val normalizedDate = kotlinx.datetime.Instant.fromEpochMilliseconds(dateMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val normalizedStart = kotlinx.datetime.Instant.fromEpochMilliseconds(startMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val normalizedEnd = kotlinx.datetime.Instant.fromEpochMilliseconds(end)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    return normalizedDate >= normalizedStart && normalizedDate <= normalizedEnd
}

fun LocalDate.toMonthYearString(): String {
    val formatter = SimpleDateFormat(DatePattern.MONTH_YEAR_FORMAT, Locale.getDefault())
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, CalendarConstants.FIRST_DAY_OF_MONTH)
    return formatter.format(calendar.time)
}

fun LocalDate.minusMonths(months: Int): LocalDate {
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, dayOfMonth)
    calendar.add(Calendar.MONTH, -months)
    return LocalDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + CalendarConstants.MONTH_INDEX_OFFSET,
        CalendarConstants.FIRST_DAY_OF_MONTH // Always use first day of month for display
    )
}

fun LocalDate.plusMonths(months: Int): LocalDate {
    val calendar = Calendar.getInstance()
    calendar.set(year, monthNumber - CalendarConstants.MONTH_INDEX_OFFSET, dayOfMonth)
    calendar.add(Calendar.MONTH, months)
    return LocalDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + CalendarConstants.MONTH_INDEX_OFFSET,
        CalendarConstants.FIRST_DAY_OF_MONTH // Always use first day of month for display
    )
}

fun LocalDate.endOfDay(): Long {
    return this.atStartOfDayIn(TimeZone.currentSystemDefault())
        .plus(1.days)
        .minus(1.milliseconds)
        .toEpochMilliseconds()
}

object DatePattern {
    const val DATE_TIME = "dd/MM/yyyy, HH:mm"
    const val INVOICE_EXPIRY = "MMM dd, h:mm a"
    const val ACTIVITY_DATE = "MMMM d"
    const val ACTIVITY_ROW_DATE = "MMMM d, HH:mm"
    const val ACTIVITY_ROW_DATE_YEAR = "MMMM d yyyy, HH:mm"
    const val ACTIVITY_TIME = "h:mm"
    const val LOG_FILE = "yyyy-MM-dd_HH-mm-ss"
    const val CHANNEL_DETAILS = "MMM d, yyyy, HH:mm"

    const val MONTH_YEAR_FORMAT = "MMMM yyyy"
    const val DATE_FORMAT = "MMM d, yyyy"
    const val WEEKDAY_FORMAT = "EEE"
}

object CalendarConstants {

    // Calendar grid
    const val DAYS_IN_WEEK = 7
    const val FIRST_DAY_OF_MONTH = 1

    // Date formatting
    const val WEEKDAY_ABBREVIATION_LENGTH = 3

    // Calendar math
    const val DAYS_IN_WEEK_MOD = 7
    const val CALENDAR_WEEK_OFFSET = 1
    const val MONTH_INDEX_OFFSET = 1

    // Leap year calculation
    const val LEAP_YEAR_DIVISOR_4 = 4
    const val LEAP_YEAR_DIVISOR_100 = 100
    const val LEAP_YEAR_DIVISOR_400 = 400

    // Preview
    const val PREVIEW_DAYS_AGO = 7
}

