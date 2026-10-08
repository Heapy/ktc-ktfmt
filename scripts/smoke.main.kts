#!/usr/bin/env kotlinr
// Run with Kotlin 2.4.21+ and JDK 25: kotlinr scripts/smoke.main.kts

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

val repo = __FILE__.canonicalFile.parentFile.parentFile
val windows = System.getProperty("os.name").startsWith("Windows")

fun temporary(prefix: String, block: (File) -> Unit) {
    val directory = Files.createTempDirectory(prefix).toFile()
    try { block(directory) } finally { directory.deleteRecursively() }
}

fun write(root: File, path: String, text: String) {
    root.resolve(path).apply { parentFile.mkdirs(); writeText(text) }
}

fun copy(source: File, destination: File) {
    check(source.copyRecursively(destination, overwrite = true)) { "Could not copy $source to $destination" }
    if (!windows) {
        source.walkTopDown().filter { it.isFile && it.canExecute() }.forEach { file ->
            val target = if (source.isDirectory) destination.resolve(file.relativeTo(source)) else destination
            check(target.setExecutable(true, false)) { "Could not preserve executable permission: $target" }
        }
    }
}

data class CommandResult(val exitCode: Int, val output: String)
fun command(directory: File, arguments: List<String>, environment: Map<String, String?> = emptyMap(), timeout: Long = 600): CommandResult {
    val log = Files.createTempFile("ktc-command-", ".log").toFile()
    try {
        val process = ProcessBuilder(arguments).directory(directory).redirectErrorStream(true).redirectOutput(log).apply {
            environment.forEach { (key, value) -> if (value == null) environment().remove(key) else environment()[key] = value }
        }.start()
        try {
            check(process.waitFor(timeout, TimeUnit.SECONDS)) { "Timed out: $arguments\n${log.readText()}" }
            return CommandResult(process.exitValue(), log.readText())
        } finally {
            if (process.isAlive) {
                process.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                process.destroyForcibly().waitFor()
            }
        }
    } finally { log.delete() }
}

fun toolchain(project: File, vararg arguments: String, succeeds: Boolean = true, diagnostic: String? = null): String {
    val wrapper = project.resolve(if (windows) "kotlin.bat" else "kotlin").absolutePath
    val invocation = if (windows) listOf("cmd.exe", "/c", wrapper) else listOf("sh", wrapper)
    val result = command(project, invocation + arguments)
    check((result.exitCode == 0) == succeeds) { "Unexpected exit ${result.exitCode}: ${arguments.toList()}\n${result.output}" }
    check(diagnostic == null || diagnostic in result.output) { "Missing diagnostic $diagnostic:\n${result.output}" }
    println("PASS: ${arguments.joinToString(" ")} (${if (succeeds) "success" else "expected failure"})")
    return result.output
}

val tool = "ktfmt"
temporary("ktc-$tool-smoke-") { root ->
    for (name in listOf("kotlin", "kotlin.bat", "plugins/$tool")) copy(repo.resolve(name), root.resolve(name))
    write(root, "project.yaml", "modules:\n  - app\n  - plugins/$tool\n\nplugins:\n  - //plugins/$tool\n")
    val app = root.resolve("app")
    for (name in listOf("src", "test", "generated")) app.resolve(name).mkdirs()
    write(app, "module.yaml", "product: jvm/lib\nplugins:\n  $tool: enabled\n")
    write(root, ".editorconfig", "root = true\n\n[*.{kt,kts}]\nindent_size = 4\n")
    val source = app.resolve("src/Greeting.kt")
    val test = app.resolve("test/GreetingTest.kt")
    val script = app.resolve("sample.kts")
    val ignored = app.resolve("generated/Skipped.kt")
    source.writeText("package sample\n\nfun greet( name:String ):String{ return \"Hello, \$name\" }\n")
    test.writeText("package sample\n\nimport kotlin.test.Test\nimport kotlin.test.assertEquals\n\nclass GreetingTest{ @Test fun greeting(){assertEquals(\"Hello, Kotlin\",greet(\"Kotlin\"))} }\n")
    script.writeText("println( 42 )\n")
    ignored.writeText("fun broken( {\n")
    val originals = listOf(source, test, script, ignored).associateWith { it.readBytes() }
    fun unchanged(files: Map<File, ByteArray>) = files.all { (file, bytes) -> file.readBytes().contentEquals(bytes) }
    toolchain(root, "build")
    check(unchanged(originals)) { "build formatted sources" }
    val output = toolchain(root, "check", "${tool}Check", "-m", "app", succeeds = false)
    check(unchanged(originals)) { "check modified sources" }
    check("Formatted " !in output) { "check invoked a format action" }
    toolchain(root, "do", "${tool}Format", "-m", "app")
    for (file in listOf(source, test, script)) check(!file.readBytes().contentEquals(originals.getValue(file))) { "not formatted: $file" }
    check(ignored.readBytes().contentEquals(originals.getValue(ignored))) { "formatted excluded file" }
    val formatted = listOf(source, test, script).associateWith { it.readBytes() }
    toolchain(root, "check")
    toolchain(root, "do", "${tool}Format", "-m", "app")
    check(unchanged(formatted)) { "format is not idempotent" }
    script.writeText("println( 43 )\n")
    toolchain(root, "check", "${tool}Check", "-m", "app", succeeds = false)
    check(script.readText() == "println( 43 )\n") { "second check modified sources" }
    println("$tool: isolated consumer smoke passed")
}
