package io.heapy.ktc.plugins.ktfmt

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
    fun formatsKotlin24DestructuringIdempotently() = inModule { root ->
        val file = root.resolve("Headers.kt")
        file.writeText("fun headers(values: Map<String, String>) {\n" +
            "    for ([name, value] in values) println(name + value)\n}\n")
        runKtfmt(root, settings, format = true)
        val formatted = file.readText()
        assertTrue(formatted.contains("[name, value]"))
        runKtfmt(root, settings, format = false)
        runKtfmt(root, settings, format = true)
        assertEquals(formatted, file.readText())
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
