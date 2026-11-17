pluginManagement {
    val flutterSdkPath = run {
        val properties = java.util.Properties()
        file("local.properties").inputStream().use { properties.load(it) }
        val flutterSdkPath = properties.getProperty("flutter.sdk")
        require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
        flutterSdkPath
    }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
}

include(":app")

val ciErrTail = java.util.Collections.synchronizedList(mutableListOf<String>())
val ciOutTail = java.util.Collections.synchronizedList(mutableListOf<String>())
gradle.beforeProject {
    logging.addStandardErrorListener(org.gradle.api.logging.StandardOutputListener { seq ->
        val s = seq.toString()
        if (s.isNotBlank()) {
            synchronized(ciErrTail) {
                ciErrTail.add(s)
                while (ciErrTail.size > 40) ciErrTail.removeAt(0)
            }
        }
    })
    logging.addStandardOutputListener(org.gradle.api.logging.StandardOutputListener { seq ->
        val s = seq.toString()
        if (s.contains("error", ignoreCase = true) || s.contains("failed", ignoreCase = true) || s.contains("exception", ignoreCase = true)) {
            synchronized(ciOutTail) {
                ciOutTail.add(s)
                while (ciOutTail.size > 30) ciOutTail.removeAt(0)
            }
        }
    })
}
@Suppress("DEPRECATION")
gradle.buildFinished {
    val err = failure
    if (err != null) {
        val sb = StringBuilder()
        var cur: Throwable? = err
        var depth = 0
        while (cur != null && depth < 10) {
            sb.append("CAUSE[$depth] ${cur.javaClass.name}: ${cur.message}\n")
            cur = cur.cause
            depth++
        }
        val errText = synchronized(ciErrTail) { ciErrTail.joinToString("") }
        if (errText.isNotEmpty()) sb.append("--- STDERR ---\n").append(errText)
        val outText = synchronized(ciOutTail) { ciOutTail.joinToString("") }
        if (outText.isNotEmpty()) sb.append("--- STDOUT_ERR ---\n").append(outText)
        val escaped = sb.toString().take(3500).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        println("::error title=Gradle Build Failure::$escaped")
    }
}
