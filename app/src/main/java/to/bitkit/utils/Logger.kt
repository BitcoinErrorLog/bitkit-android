package to.bitkit.utils

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.lightningdevkit.ldknode.LogRecord
import org.lightningdevkit.ldknode.LogWriter
import to.bitkit.async.ServiceQueue
import to.bitkit.async.newSingleThreadDispatcher
import to.bitkit.di.json
import to.bitkit.env.Env
import to.bitkit.ext.DatePattern
import to.bitkit.ext.utcDateFormatterOf
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import org.lightningdevkit.ldknode.LogLevel as LdkLogLevel

private const val APP = "APP"
private const val LDK = "LDK"
private const val COMPACT = false

enum class LogSource { Ldk, Bitkit, Unknown }
enum class LogLevel { PERF, VERBOSE, GOSSIP, TRACE, DEBUG, INFO, WARN, ERROR; }

object Logger {
    private val delegate by lazy { LoggerImpl(APP, saver = LogSaverImpl(buildSessionLogFilePath(LogSource.Bitkit))) }

    fun info(
        msg: String?,
        context: String = "",
        file: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) = delegate.info(msg, context, file, line)

    fun debug(
        msg: String?,
        context: String = "",
        file: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) = delegate.debug(msg, context, file, line)

    fun warn(
        msg: String?,
        e: Throwable? = null,
        context: String = "",
        file: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) = delegate.warn(msg, e, context, file, line)

    fun error(
        msg: String?,
        e: Throwable? = null,
        context: String = "",
        file: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) = delegate.error(msg, e, context, file, line)

    fun verbose(
        msg: String?,
        e: Throwable? = null,
        context: String = "",
        file: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) = delegate.verbose(msg, e, context, file, line)

    fun performance(
        msg: String?,
        context: String = "",
        file: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) = delegate.performance(msg, context, file, line)
}

class LoggerImpl(
    private val tag: String = APP,
    private val saver: LogSaver,
    private val compact: Boolean = COMPACT,
) {
    fun info(
        msg: String?,
        context: String = "",
        path: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) {
        val message = formatLog(LogLevel.INFO, msg, context, path, line)
        Log.i(tag, message)
        saver.save(message)
    }

    fun debug(
        msg: String?,
        context: String = "",
        path: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) {
        val message = formatLog(LogLevel.DEBUG, msg, context, path, line)
        Log.d(tag, message)
        saver.save(message)
    }

    fun warn(
        msg: String?,
        e: Throwable? = null,
        context: String = "",
        path: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) {
        val errMsg = e?.let { "[${e::class.simpleName}='${e.message}']" }.orEmpty()
        val message = formatLog(LogLevel.WARN, "$msg $errMsg", context, path, line)
        if (compact) Log.w(tag, message) else Log.w(tag, message, e)
        saver.save(message)
    }

    fun error(
        msg: String?,
        e: Throwable? = null,
        context: String = "",
        path: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) {
        val errMsg = e?.let { "[${e::class.simpleName}='${e.message}']" }.orEmpty()
        val message = formatLog(LogLevel.ERROR, "$msg $errMsg", context, path, line)
        if (compact) Log.e(tag, message) else Log.e(tag, message, e)
        saver.save(message)
    }

    @Suppress("LongParameterList")
    fun verbose(
        msg: String?,
        e: Throwable? = null,
        context: String = "",
        path: String = getCallerPath(),
        line: Int = getCallerLine(),
        level: LogLevel = LogLevel.VERBOSE,
    ) {
        val message = formatLog(level, msg, context, path, line)
        if (compact) Log.v(tag, message) else Log.v(tag, message, e)
        saver.save(message)
    }

    fun performance(
        msg: String?,
        context: String = "",
        path: String = getCallerPath(),
        line: Int = getCallerLine(),
    ) {
        val message = formatLog(LogLevel.PERF, msg, context, path, line)
        Log.v(tag, message)
        saver.save(message)
    }
}

interface LogSaver {
    fun save(message: String)
}

class LogSaverImpl(private val sessionFilePath: String) : LogSaver {
    private val queue: CoroutineScope by lazy {
        CoroutineScope(newSingleThreadDispatcher(ServiceQueue.LOG.name) + SupervisorJob())
    }

    init {
        // Clean all old log files in background
        CoroutineScope(Dispatchers.IO).launch {
            cleanupOldLogFiles()
        }
    }

    override fun save(message: String) {
        queue.launch {
            runCatching {
                FileOutputStream(File(sessionFilePath), true).use { stream ->
                    stream.write("$message\n".toByteArray())
                }
            }.onFailure {
                Log.e(APP, "Error writing to log file: '$sessionFilePath'", it)
            }
        }
    }

    private fun cleanupOldLogFiles(maxTotalSizeMB: Int = 20) {
        Log.v(APP, "Deleting old log files…")
        val baseDir = File(Env.logDir)
        if (!baseDir.exists()) return

        val logFiles = baseDir
            .listFiles { file -> file.extension == "log" }
            ?.map { file -> Triple(file, file.length(), file.lastModified()) }
            ?: return

        var totalSize = logFiles.sumOf { it.second }
        val maxSizeBytes = maxTotalSizeMB * 1024L * 1024L

        // Sort by creation date (oldest first)
        logFiles
            .sortedBy { it.third }
            .forEach { (file, size, _) ->
                if (totalSize <= maxSizeBytes) return

                runCatching {
                    Log.d(APP, "Deleting old log file: '${file.name}'")
                    if (file.delete()) {
                        totalSize -= size
                    }
                }.onFailure {
                    Log.w(APP, "Failed to delete old log file: '${file.name}'", it)
                }
            }
        Log.v(APP, "Deleted all old log files.")
    }
}

class LdkLogWriter(
    private val maxLogLevel: LdkLogLevel = Env.ldkLogLevel,
    saver: LogSaver = LogSaverImpl(buildSessionLogFilePath(LogSource.Ldk)),
) : LogWriter {
    private val delegate: LoggerImpl = LoggerImpl(LDK, saver)

    override fun log(record: LogRecord) {
        if (record.level < maxLogLevel) return

        val msg = record.args
        val path = record.modulePath
        val line = record.line.toInt()

        when (record.level) {
            LdkLogLevel.GOSSIP -> delegate.verbose(msg, path = path, line = line, level = LogLevel.GOSSIP)
            LdkLogLevel.TRACE -> delegate.verbose(msg, path = path, line = line, level = LogLevel.TRACE)
            LdkLogLevel.DEBUG -> delegate.debug(msg, path = path, line = line)
            LdkLogLevel.INFO -> delegate.info(msg, path = path, line = line)
            LdkLogLevel.WARN -> delegate.warn(msg, path = path, line = line)
            LdkLogLevel.ERROR -> delegate.error(msg, path = path, line = line)
        }
    }
}

private fun buildSessionLogFilePath(source: LogSource): String {
    val sourceName = source.name.lowercase()
    val timestamp = utcDateFormatterOf(DatePattern.LOG_FILE).format(Date())
    val sessionLogFilePath = File(Env.logDir).resolve("${sourceName}_$timestamp.log").path
    Log.i(APP, "Log session for '$sourceName' initialized with file path: '$sessionLogFilePath'")
    return sessionLogFilePath
}

private fun formatLog(level: LogLevel, msg: String?, context: String, path: String, line: Int): String {
    val timestamp = utcDateFormatterOf(DatePattern.LOG_LINE).format(Date())
    val message = msg?.trim().orEmpty()
    val contextString = if (context.isNotEmpty()) " - $context" else ""
    return String.format(
        Locale.US,
        "%s %-7s [%s:%d] %s%s",
        timestamp,
        level.name,
        path,
        line,
        message,
        contextString,
    )
}

/**
 * Determines which stack frame to use for caller info.
 *
 * The value 5 is chosen based on the current call chain:
 * - [0] `Thread.getStackTrace`
 * - [1] `getCallerPath`/`getCallerLine`
 * - [2] `LoggerImpl.log_method` (e.g., debug, info, etc.)
 * - [3] `LdkLogWriter.log_method`
 * - [4] external caller
 * - [5] actual caller of logging function
 *
 * If the call chain changes, this index may need to be updated.
 */

private const val STACK_INDEX = 5
private fun getCallerPath(): String = Thread.currentThread().stackTrace.getOrNull(STACK_INDEX)?.fileName ?: "Unknown"
private fun getCallerLine(): Int = Thread.currentThread().stackTrace.getOrNull(STACK_INDEX)?.lineNumber ?: -1

val jsonLogger = Json(json) {
    prettyPrint = false
}

inline fun <reified T> jsonLogOf(value: T): String = with(jsonLogger) {
    encodeToString(serializersModule.serializer(), value)
}
