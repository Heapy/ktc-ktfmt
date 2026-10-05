#!/usr/bin/env python3
"""Exercise a copied standalone plugin with a real Kotlin Toolchain consumer."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

REPO = Path(__file__).resolve().parents[1]
TOOL = "ktfmt"


def main():
    with tempfile.TemporaryDirectory(prefix=f"ktc-{TOOL}-smoke-") as directory:
        root = Path(directory)
        for wrapper in ("kotlin", "kotlin.bat"):
            shutil.copy2(REPO / wrapper, root / wrapper)
        shutil.copytree(REPO / "plugins" / TOOL, root / "plugins" / TOOL)
        (root / "project.yaml").write_text(
            f"modules:\n  - app\n  - plugins/{TOOL}\n\nplugins:\n  - //plugins/{TOOL}\n"
        )
        app = root / "app"
        (app / "src").mkdir(parents=True)
        (app / "test").mkdir()
        (app / "generated").mkdir()
        (app / "module.yaml").write_text(f"product: jvm/lib\nplugins:\n  {TOOL}: enabled\n")
        (root / ".editorconfig").write_text("root = true\n\n[*.{kt,kts}]\nindent_size = 4\n")
        source = app / "src" / "Greeting.kt"
        test = app / "test" / "GreetingTest.kt"
        script = app / "sample.kts"
        ignored = app / "generated" / "Skipped.kt"
        source.write_text('package sample\n\nfun greet( name:String ):String{ return "Hello, $name" }\n')
        test.write_text('package sample\n\nimport kotlin.test.Test\nimport kotlin.test.assertEquals\n\nclass GreetingTest{ @Test fun greeting(){assertEquals("Hello, Kotlin",greet("Kotlin"))} }\n')
        script.write_text("println( 42 )\n")
        ignored.write_text("fun broken( {\n")
        originals = {p: p.read_bytes() for p in (source, test, script, ignored)}
        wrapper = str(root / ("kotlin.bat" if os.name == "nt" else "kotlin"))

        def run(*args, succeeds=True):
            result = subprocess.run([wrapper, *args], cwd=root, text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            print(result.stdout, end="", flush=True)
            if (result.returncode == 0) != succeeds:
                raise AssertionError(f"Unexpected exit {result.returncode}: {args}")
            return result.stdout

        run("build")
        assert all(p.read_bytes() == content for p, content in originals.items()), "build formatted sources"
        output = run("check", f"{TOOL}Check", "-m", "app", succeeds=False)
        assert all(p.read_bytes() == content for p, content in originals.items()), "check modified sources"
        assert "Formatted " not in output, "check invoked a format action"
        run("do", f"{TOOL}Format", "-m", "app")
        for file in (source, test, script):
            assert file.read_bytes() != originals[file], f"not formatted: {file}"
        assert ignored.read_bytes() == originals[ignored], "formatted excluded file"
        formatted = {p: p.read_bytes() for p in (source, test, script)}
        run("check")
        run("do", f"{TOOL}Format", "-m", "app")
        assert all(p.read_bytes() == content for p, content in formatted.items()), "format is not idempotent"
        # A new violation after a successful run must not be skipped as up-to-date.
        script.write_text("println( 43 )\n")
        run("check", f"{TOOL}Check", "-m", "app", succeeds=False)
        assert script.read_text() == "println( 43 )\n", "second check modified sources"
        print(f"{TOOL}: isolated consumer smoke passed", flush=True)


if __name__ == "__main__":
    main()
