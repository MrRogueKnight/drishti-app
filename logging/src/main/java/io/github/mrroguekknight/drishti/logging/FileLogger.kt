package io.github.mrroguekknight.drishti.logging

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A simple logger that writes messages to a file in the app's cache directory.
 */
class FileLogger(context: Context) {

    private val logDirectory = File(context.cacheDir, "logs").apply {
        if (!exists()) mkdirs()
    }

    private var currentLogFile: File? = null
    private var fileWriter: FileWriter? = null

    init {
        createNewLogFile()
    }

    private fun createNewLogFile() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        currentLogFile = File(logDirectory, "log_$timestamp.txt")
        try {
            fileWriter = FileWriter(currentLogFile, true) // Append mode
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Logs a message to the current log file.
     * TODO: Add JSON serialization and file rotation logic.
     */
    fun log(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            fileWriter?.apply {
                write("$timestamp: $message\n")
                flush()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Closes the current log file.
     */
    fun close() {
        try {
            fileWriter?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
