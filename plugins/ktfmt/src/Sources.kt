package dev.ktc.plugins.ktfmt

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.invariantSeparatorsPathString

/** Discover only files owned by this module, without following symbolic links. */
internal fun kotlinSources(root: Path, includes: List<String>, excludes: List<String>): List<Path> {
    require(Files.isDirectory(root)) { "Module root is not a directory: $root" }
    val includeMatchers = includes.map { glob(root, it) }
    val excludeMatchers = excludes.map { glob(root, it) }
    val files = mutableListOf<Path>()
    val ignoredDirectories = setOf("build", ".git", ".kotlin", ".gradle", ".idea", "node_modules")
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (dir != root && (dir.fileName.toString() in ignoredDirectories || Files.exists(dir.resolve("module.yaml")))) {
                return FileVisitResult.SKIP_SUBTREE
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val relative = root.relativize(file)
            if (attrs.isRegularFile && (file.toString().endsWith(".kt") || file.toString().endsWith(".kts")) &&
                includeMatchers.any { it(relative) } && excludeMatchers.none { it(relative) }
            ) {
                files.add(file)
            }
            return FileVisitResult.CONTINUE
        }
    })
    // Keep source order identical on Windows and Unix, including src and src@platform.
    return files.sortedBy { root.relativize(it).invariantSeparatorsPathString }
}

private fun glob(root: Path, pattern: String): (Path) -> Boolean {
    require(pattern.isNotBlank() && !pattern.startsWith("/") && !pattern.contains('\\')) {
        "Use a non-empty, module-relative glob with '/' separators: $pattern"
    }
    val matcher = root.fileSystem.getPathMatcher("glob:$pattern")
    // In Java globs, **/ requires at least one directory. Also match files at the module root.
    val rootMatcher = pattern.takeIf { it.startsWith("**/") }
        ?.removePrefix("**/")?.let { root.fileSystem.getPathMatcher("glob:$it") }
    return { path -> matcher.matches(path) || rootMatcher?.matches(path) == true }
}
