# ktc-ktfmt

A standalone local [Kotlin Toolchain](https://github.com/JetBrains/kotlin-toolchain)
plugin for ktfmt 0.64. Pinned and tested with Kotlin Toolchain **0.13.0**.
Includes a real consumer example, a reusable module template, and producer metadata
for source-based plugin installers. This is an independent repository.

## Try it

Install a full JDK 17+ and expose it through `JAVA_HOME` or `PATH`. Toolchain's
bundled JRE does not include the `jdk.compiler` module required by ktfmt. The plugin
launches a child JVM using `JAVA_HOME/bin/java`, falling back to `java` on `PATH`.
An explicit `javaExecutable` overrides both.

```sh
./kotlin build
./kotlin check
./kotlin do ktfmtFormat -m example
kotlinr scripts/smoke.main.kts
```

On Windows use `kotlin.bat`. `check` runs unit tests plus `ktfmtCheck` for enabled
modules. Narrow a check with `./kotlin check ktfmtCheck -m example`.
Formatting is an explicit source-writing command; building and checking never
invoke it. Choose one formatter for each source tree: ktlint and ktfmt can disagree
on formatting conventions.

## Install in another project

From your consumer project, use the [ktc-plugins installer](https://github.com/Heapy/ktc-plugins):

```sh
./ktc-plugins add Heapy/ktc-ktfmt --branch main --enable-in app
```

Replace `app` with your consumer module path. The root [`ktc-plugin.yaml`](ktc-plugin.yaml)
declares selector `ktfmt`, module `plugins/ktfmt`, and `LICENSE`; the installer registers
and enables the plugin automatically. Commit the generated manifest, lockfile, and vendored
sources. The lockfile pins the resolved commit; use `--commit <full-40-character-SHA>`
instead of `--branch main` to select a specific revision. Templates are copied separately.

For manual installation:

Copy `plugins/ktfmt/` to the same path in the consumer and retain this repository's
`LICENSE`. The plugin module is self-contained: all dependencies use pinned Maven
coordinates, and it requires no root template, catalog, or other local module.
Register the copied module in **both** lists in the consumer's `project.yaml`:

```yaml
modules:
  - app
  - plugins/ktfmt

plugins:
  - //plugins/ktfmt
```

Enable it in `app/module.yaml`:

```yaml
plugins:
  ktfmt:
    enabled: true
    includes: ["**/*.kt", "**/*.kts"]
    excludes: ["**/generated/**"]
    style: kotlinlang # kotlinlang | google | meta
    maxWidth: 100
    removeUnusedImports: true
    javaExecutable: "" # optional absolute path to a full JDK's java executable
```

For common settings, optionally copy `templates/ktfmt.module-template.yaml` and
apply it from modules with `apply: [//templates/ktfmt.module-template.yaml]`.
`ktc-plugin.yaml` declares selector `ktfmt`, module path `plugins/ktfmt`, and
`licenseFiles: [LICENSE]` for the source installer.

## Files and execution

Discovery starts at the enabled module's directory and includes `.kt` and `.kts`
files in main sources, tests, platform directories, Maven-style layouts, and
module-root scripts. It always skips directories named `build`, `.git`, `.kotlin`,
`.gradle`, `.idea`, and `node_modules`, plus nested modules with their own
`module.yaml`. Symlinks are not followed. Generated directories are excluded by
the default glob; add project-specific output directories to `excludes` when needed.

Globs are Java NIO globs relative to the module root, using `/` separators. A leading
`**/` also matches zero directories. `includes` selects files and `excludes` removes
matches. The lists above are defaults; configuration replaces those lists.
Discovery is source-based and can inspect platform directories without compiling
that target. It does not inspect Java, generated build outputs, or dependencies.

Both actions disable execution avoidance so source and parent configuration changes
are always observed. Their root inputs disable task-dependency inference, and format
actions declare no source outputs. This prevents the toolchain from treating a
format action as a prerequisite for compilation or checking. Within each module, formatting computes
all results before writing, so a parse error leaves that module unchanged. Other
modules can still finish formatting. Filesystem write failures can leave some completed writes.

Formatting uses ktfmt's library API with the selected preset. Configuration comes
from the plugin YAML; this integration does **not** read `.editorconfig` (the upstream
CLI's EditorConfig integration is separate). The check computes the formatted text
in memory and fails when it differs, without writing files. Diagnostics list each
file requiring formatting. ktfmt is a formatter, not a semantic lint engine.

The embedded upstream Kotlin parser determines supported source syntax; newer
language constructs may need a dependency upgrade even when Toolchain compiles
them. Upgrade the literal versions in `plugins/ktfmt/module.yaml` together with
these checks. Current dependencies: `com.facebook:ktfmt:0.64`.

**Known compatibility gap:** ktfmt 0.64 embeds the Kotlin 2.3.20 parser and rejects
the bracket destructuring used by Kotlin 2.4.20 with
`-Xname-based-destructuring=complete`, such as `for ([name, value] in headers)`.
This prevents checking or formatting the current Kotgent application root. Both
operations fail without source writes; installing this plugin does not imply
support for every language feature in Toolchain 0.13.0. The real-project trial
and successful checks on Kotgent's other modules are recorded below.

## Verification and maintenance

`./kotlin check` exercises source discovery, include/exclude boundaries, scripts,
read-only failed checks, formatting idempotence, and syntax-error behavior. The
isolated consumer smoke test copies only the plugin module and wrapper into a fresh
project and proves that build/check do not format, checks fail on bad formatting,
explicit formatting fixes main/test/script files, generated exclusions survive,
consumer tests pass, and a later violation is detected.

CI repeats build, check, and the smoke test on Linux, macOS, and Windows, using
pinned checkout and [Heapy/setup-ktc](https://github.com/Heapy/setup-ktc) commits.
Dependabot tracks action updates. Local verification is recorded in
[docs/verification.md](docs/verification.md).

## Upstream and license

- [ktfmt 0.64 release](https://github.com/Kotlin/ktfmt/releases/tag/v0.64)
- [ktfmt formatter API at the pinned tag](https://github.com/Kotlin/ktfmt/blob/v0.64/core/src/main/java/com/facebook/ktfmt/format/Formatter.kt)
- [ktfmt formatting options](https://github.com/Kotlin/ktfmt/blob/v0.64/core/src/main/java/com/facebook/ktfmt/format/FormattingOptions.kt)
- [Toolchain 0.13.0](https://github.com/JetBrains/kotlin-toolchain/tree/v0.13.0)

Apache-2.0; see [LICENSE](LICENSE). Upstream dependencies retain their own licenses.

## Running verification scripts

The `.main.kts` scripts require JDK 25 and Kotlin 2.4.21+ (`kotlinr` on `PATH`).
Run them with `kotlinr scripts/<name>.main.kts` from the repository root.
The Kotlin Toolchain `./kotlin` command is a separate executable. CI installs the script runner
through [Heapy/setup-main-kts](https://github.com/Heapy/setup-main-kts), pinned to v1.0.1's
commit SHA. The action caches the compiler, Maven dependencies, and compiled scripts between
eligible CI runs. The first script run compiles the script and resolves any pinned Maven
dependencies; later runs reuse the script cache.
