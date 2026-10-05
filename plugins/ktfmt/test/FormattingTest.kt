package dev.ktc.plugins.ktfmt

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FormattingTest {
    private val settings = object : KtfmtSettings {}

    private fun inModule(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("ktc-format-test-")
        root.resolve(".editorconfig").writeText("root = true\n\n[*.{kt,kts}]\nindent_size = 4\n")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }

    @Test
    fun checkFailsWithoutChangingBytesThenFormatIsIdempotent() = inModule { root ->
        val file = root.resolve("Example.kt")
        val original = "fun answer( )=42\n"
        file.writeText(original)
        assertFailsWith<IllegalStateException> { runKtfmt(root, settings, format = false) }
        assertEquals(original, file.readText())
        runKtfmt(root, settings, format = true)
        val formatted = file.readText()
        assertNotEquals(original, formatted)
        runKtfmt(root, settings, format = false)
        runKtfmt(root, settings, format = true)
        assertEquals(formatted, file.readText())
    }

    @Test
    fun parseFailureDoesNotPartiallyFormatOtherFiles() = inModule { root ->
        val original = "fun answer( )=42\n"
        root.resolve("A.kt").writeText(original)
        root.resolve("Z.kt").writeText("fun broken( {\n")
        assertFailsWith<IllegalStateException> { runKtfmt(root, settings, format = true) }
        assertEquals(original, root.resolve("A.kt").readText())
    }

    @Test
    fun unsupportedKotlin24DestructuringFailsBeforeAnyWrites() = inModule { root ->
        // Reduced from Kotgent's HttpPushTransport.kt. Revisit this expected failure when
        // upgrading ktfmt: the pinned 0.64 engine uses the Kotlin 2.3.20 parser.
        val earlier = root.resolve("A.kt")
        val modern = root.resolve("Z.kt")
        val originalEarlier = "fun answer( )=42\n"
        val originalModern = "fun headers(values: Map<String, String>) {\n" +
            "    for ([name, value] in values) println(name + value)\n}\n"
        earlier.writeText(originalEarlier)
        modern.writeText(originalModern)
        for (format in listOf(false, true)) {
            val failure = assertFailsWith<IllegalStateException> {
                runKtfmt(root, settings, format)
            }
            assertTrue(failure.message.orEmpty().contains("Cannot format $modern:"))
            assertTrue(failure.cause is com.facebook.ktfmt.format.ParseError)
            assertEquals(originalEarlier, earlier.readText())
            assertEquals(originalModern, modern.readText())
        }
    }

    @Test
    fun formatsKotlinScripts() = inModule { root ->
        root.resolve("script.kts").writeText("println( 42 )\n")
        runKtfmt(root, settings, format = true)
        runKtfmt(root, settings, format = false)
    }

    @Test
    fun selectsGoogleStyle() = inModule { root ->
        val file = root.resolve("Example.kt")
        file.writeText("fun answer(){println(42)}\n")
        runKtfmt(root, object : KtfmtSettings { override val style = "google" }, format = true)
        assertEquals("fun answer() {\n  println(42)\n}\n", file.readText())
    }

    @Test
    fun rejectsInvalidStyleWithoutChangingSources() = inModule { root ->
        val file = root.resolve("Example.kt")
        val original = "fun answer( )=42\n"
        file.writeText(original)
        assertFailsWith<IllegalStateException> { runKtfmt(root, object : KtfmtSettings { override val style = "missing" }, format = true) }
        assertEquals(original, file.readText())
    }
}
