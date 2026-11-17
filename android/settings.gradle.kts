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
val loggingOut = (gradle as org.gradle.api.internal.GradleInternal).services.get(
    org.gradle.internal.logging.LoggingOutputInternal::class.java
)
val ciEventListener = org.gradle.internal.logging.events.OutputEventListener { event ->
    val txt = event.toString()
    if (event is org.gradle.internal.logging.events.RenderableOutputEvent) {
        if (event.logLevel == org.gradle.api.logging.LogLevel.ERROR ||
            txt.contains("e: ", ignoreCase = false) ||
            txt.contains("error:", ignoreCase = true) ||
            txt.contains("Unresolved", ignoreCase = false)
        ) {
            synchronized(ciErrTail) {
                ciErrTail.add(txt)
                while (ciErrTail.size > 50) ciErrTail.removeAt(0)
            }
        }
    }
}
loggingOut.addOutputEventListener(ciEventListener)
@Suppress("DEPRECATION")
gradle.buildFinished {
    loggingOut.removeOutputEventListener(ciEventListener)
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
        val errText = synchronized(ciErrTail) { ciErrTail.joinToString("\n") }
        if (errText.isNotEmpty()) sb.append("--- EVENTS ---\n").append(errText)
        val escaped = sb.toString().take(3500).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        println("::error title=Gradle Build Failure::$escaped")
    }
}
