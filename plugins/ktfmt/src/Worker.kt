package io.heapy.ktc.plugins.ktfmt

import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Toolchain runs plugins in its bundled JRE, which lacks jdk.compiler. google-java-format
 * requires that module, so launch a worker with the user's full JDK 17+.
 */
internal fun runInWorker(root: Path, settings: KtfmtSettings, format: Boolean) {
    val loader = KtfmtSettings::class.java.classLoader
    check(loader is URLClassLoader) { "Expected Kotlin Toolchain 0.13's URL plugin classloader" }
    val classpath = loader.urLs.joinToString(File.pathSeparator) { Path.of(it.toURI()).toString() }
    val executableName = if (File.separatorChar == '\\') "java.exe" else "java"
    val java = settings.javaExecutable.ifBlank {
        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it, "bin", executableName).toString() } ?: executableName
    }
    val arguments = listOf(
        java, "--add-modules=jdk.compiler", "-cp", classpath, "io.heapy.ktc.plugins.ktfmt.WorkerKt",
        root.toString(), format.toString(), settings.style, settings.maxWidth.toString(),
        settings.removeUnusedImports.toString(), settings.includes.size.toString(),
    ) + settings.includes + settings.excludes
    val process = try {
        ProcessBuilder(arguments).directory(root.toFile()).inheritIO().start()
    } catch (e: IOException) {
        throw IllegalStateException(
            "Cannot launch ktfmt with '$java'. Set JAVA_HOME or plugins.ktfmt.javaExecutable to a full JDK 17+.",
            e,
        )
    }
    val exitCode = try {
        process.waitFor()
    } catch (e: InterruptedException) {
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        throw e
    }
    check(exitCode == 0) {
        "ktfmt ${if (format) "format" else "check"} failed with exit code $exitCode (see diagnostics above)"
    }
}

fun main(args: Array<String>) {
    require(args.size >= 6) { "This entry point is internal to the ktfmt plugin" }
    val includeCount = args[5].toInt()
    require(includeCount >= 0 && args.size >= 6 + includeCount)
    val settings = object : KtfmtSettings {
        override val style: String = args[2]
        override val maxWidth: Int = args[3].toInt()
        override val removeUnusedImports: Boolean = args[4].toBooleanStrict()
        override val includes: List<String> = args.slice(6 until 6 + includeCount)
        override val excludes: List<String> = args.drop(6 + includeCount)
    }
    runKtfmt(Path.of(args[0]), settings, args[1].toBooleanStrict())
}
