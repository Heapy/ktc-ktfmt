package dev.ktc.plugins.ktfmt

import com.facebook.ktfmt.format.Formatter
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.jetbrains.amper.plugins.Configurable
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction

@Configurable
interface KtfmtSettings {
    val includes: List<String> get() = listOf("**/*.kt", "**/*.kts")
    val excludes: List<String> get() = listOf("**/generated/**")
    val style: String get() = "kotlinlang"
    val maxWidth: Int get() = 100
    val removeUnusedImports: Boolean get() = true
    val javaExecutable: String get() = ""
}

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun checkFormatting(
    @Input(inferTaskDependency = false) moduleRoot: Path,
    settings: KtfmtSettings,
) {
    runInWorker(moduleRoot, settings, format = false)
}

// Formatting modifies source inputs deliberately. Declaring them as outputs would wire format
// into compilation/check tasks through path inference. Always run it only on explicit request.
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun formatSources(
    @Input(inferTaskDependency = false) moduleRoot: Path,
    settings: KtfmtSettings,
) {
    runInWorker(moduleRoot, settings, format = true)
}

internal fun runKtfmt(moduleRoot: Path, settings: KtfmtSettings, format: Boolean) {
    require(settings.maxWidth > 0) { "maxWidth must be positive" }
    val preset = when (settings.style) {
        "kotlinlang" -> Formatter.KOTLINLANG_FORMAT
        "google" -> Formatter.GOOGLE_FORMAT
        "meta" -> Formatter.META_FORMAT
        else -> error("Unknown ktfmt style '${settings.style}'; expected kotlinlang, google, or meta")
    }
    val options = preset.toBuilder()
        .maxWidth(settings.maxWidth)
        .removeUnusedImports(settings.removeUnusedImports)
        .build()
    val files = kotlinSources(moduleRoot, settings.includes, settings.excludes)
    val updates = files.mapNotNull { file ->
        val original = file.readText()
        val formatted = try {
            Formatter.format(options, original)
        } catch (e: com.facebook.ktfmt.format.ParseError) {
            throw IllegalStateException("Cannot format $file: ${e.message}", e)
        }
        if (original == formatted) null else file to formatted
    }
    if (format) {
        // Compute all results first, so a syntax error cannot cause a partial format run.
        for ((file, formatted) in updates) {
            file.writeText(formatted)
            println("Formatted ${moduleRoot.relativize(file)}")
        }
    } else {
        updates.forEach { (file, _) -> System.err.println("$file: requires ktfmt formatting") }
        check(updates.isEmpty()) {
            "ktfmt found ${updates.size} unformatted file(s). Run './kotlin do ktfmtFormat'."
        }
    }
    println("ktfmt checked ${files.size} Kotlin file(s); formatted ${if (format) updates.size else 0}.")
}
