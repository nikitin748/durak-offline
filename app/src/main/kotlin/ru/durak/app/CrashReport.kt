package ru.durak.app

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves the last crash to a file so the next launch can show it (no adb needed). */
class DurakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(filesDir, "crash.txt").writeText(
                    "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}, ${Build.VERSION.CODENAME}) ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${t.name}\n\n$sw"
                )
            }
            old?.uncaughtException(t, e)
        }
    }
}

fun Context.pendingCrash(): String? = runCatching { File(filesDir, "crash.txt").takeIf { it.exists() }?.readText() }.getOrNull()
fun Context.clearCrash() { runCatching { File(filesDir, "crash.txt").delete() } }
