# Verification

Verified locally on 2026-10-05, macOS arm64, Kotlin Toolchain 0.13.0, JDK 25.
Pinned engine: ktfmt 0.64.

- `./kotlin build`: plugin and example main/test sources compile.
- `./kotlin check`: 9 plugin tests and 1 example test pass; example `ktfmtCheck` passes.
- `kotlinr scripts/smoke.main.kts`: isolated copied-plugin consumer builds and tests;
  failed formatting checks preserve source bytes; explicit format fixes main,
  test, and script files; generated exclusion stays untouched; a second format
  changes no bytes; a new violation fails the next check.

The ktfmt action launches its engine in a full JDK because the Toolchain wrapper's
bundled JRE lacks `jdk.compiler`. The local test environment uses a JDK 25 at
`JAVA_HOME`; the plugin worker bytecode targets Java 17. Unit tests and real plugin
tasks both passed. Setting an explicit `plugins.ktfmt.javaExecutable` is available
for environments without `JAVA_HOME` or a JDK on `PATH`.

The macOS agent sandbox denied Toolchain's `sysctl` process inspection; actual
build and task verification ran with permission for compiler subprocess management.
Kotlin's embedded compiler emits an upstream `sun.misc.Unsafe` deprecation warning
on JDK 25; it did not fail these checks.

The three-OS GitHub Actions matrix is included but has not been run remotely.
Linux/Windows runtime behavior is not established by this local verification.
No release or registry operation was part of these checks.

## Real-project trial: Kotgent

On 2026-10-05, copied `plugins/ktfmt` into an isolated detached Kotgent worktree
at `ac1f35a21af210c0579b3536f326da96dace0ebb`. Registered the plugin and enabled
it on the actual Native application root, `sysnative`, and JVM `build-info`
modules. Kept Kotlin 2.4.20 and `-Xname-based-destructuring=complete` unchanged.
All Kotlin CLI calls were serialized through Kotgent's `kotlin-build` mutex.

**Overall result: partial support; the application root is incompatible.**
`./kotlin check ktfmtCheck -m kotgent-ktfmt -m sysnative -m build-info` failed
because ktfmt's Kotlin 2.3.20 parser rejects the real
`src/push/HttpPushTransport.kt:18` statement
`for ([name, value] in headers) header(name, value)`. This is a parser failure,
not a formatting disagreement. `./kotlin do ktfmtFormat -m kotgent-ktfmt` failed
at the same statement. SHA-256 snapshots of all 394 Kotlin source files in the
worktree proved both operations made zero source changes.

The other modules had ordinary formatting differences. Explicit
`./kotlin do ktfmtFormat -m sysnative -m build-info` inspected 12 and 4 files,
respectively, and formatted 8 and 3. Changes included main sources, ordinary
tests, and `test@linux`/`test@macos` files. Only sources in those two selected
modules changed. Their next `ktfmtCheck` passed; repeating the same format
command changed no bytes. No application sources were excluded to obtain this
result, and the application root's unrestricted configuration remains enabled.

After formatting, `./kotlin build -m sysnative -m build-info -p macosArm64 -p jvm`
and the corresponding `./kotlin test` passed. These are actual Kotgent modules
and existing tests; no daemon or real model session was started. A reduced
regression from the failing application statement now proves that both check
and format preserve all files when the pinned parser cannot read Kotlin 2.4
bracket destructuring. `./kotlin test -m ktfmt` passed all 10 plugin tests.

The preserved local worktree is
`ktc-plugin-trials/2026-10-05/kotgent-ktfmt`; `result.json`
records commands and outcomes, and `.trial/` retains logs and source snapshots.
These are maintainer-local artifacts, not files in this repository.
The sandbox's `sysctl` restriction required compiler-subprocess permission;
`JAVA_HOME` pointed to a full JDK 25 installation.
The Native application was not built or reformatted successfully: an upstream
formatter/parser upgrade is needed before adopting this plugin for its current
language features. Neither the language configuration nor upstream parser
dependencies were overridden during the trial.
