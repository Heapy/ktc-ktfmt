package io.heapy.ktc.plugins.ktfmt

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourcesTest {
    private fun inModule(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("ktc-sources-test-")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }

    private fun file(root: Path, path: String) {
        val file = root.resolve(path)
        file.parent.createDirectories()
        file.writeText("")
    }

    @Test
    fun discoversSourcesTestsScriptsAndPlatformDirectoriesInPortableOrder() = inModule { root ->
        val expected = listOf("build.main.kts", "src/Hello.kt", "src@jvm/Jvm.kt", "test/Test.kt", "test@jvm/JvmTest.kt")
        expected.reversed().forEach { file(root, it) }
        file(root, "src/Java.java")
        val found = kotlinSources(root, listOf("**/*.kt", "**/*.kts"), emptyList())
        assertEquals(expected, found.map { root.relativize(it).invariantSeparatorsPathString })
    }

    @Test
    fun excludesBuildGeneratedAndNestedModules() = inModule { root ->
        listOf("src/Hello.kt", "build/Bad.kt", ".git/Bad.kt", "src/generated/Bad.kt", "generated/Bad.kt", "nested/module.yaml", "nested/Bad.kt")
            .forEach { file(root, it) }
        val found = kotlinSources(root, listOf("**/*.kt"), listOf("**/generated/**"))
        assertEquals(listOf(root.resolve("src/Hello.kt")), found)
    }

    @Test
    fun supportsIncludeAndExcludeGlobs() = inModule { root ->
        listOf("src/Hello.kt", "src/Ignored.kt", "test/Test.kt").forEach { file(root, it) }
        assertEquals(listOf(root.resolve("src/Hello.kt")),
            kotlinSources(root, listOf("src/**"), listOf("**/Ignored.kt")))
    }

    @Test
    fun rejectsAbsoluteGlob() = inModule { root ->
        assertFailsWith<IllegalArgumentException> { kotlinSources(root, listOf("/tmp/*.kt"), emptyList()) }
    }
}
