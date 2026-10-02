"""Publica una nueva version de Interfon en GitHub Releases.

Hace: bump de VERSION -> gradle versionCode/versionName -> assembleRelease
firmado -> copia a dist/ -> commit + tag -> gh release con el APK.

Uso (desde la raiz del proyecto):
  python scripts/publish_release.py patch [--notes "texto"]
  python scripts/publish_release.py minor
  python scripts/publish_release.py major
"""
import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "VERSION"
GRADLE = ROOT / "android" / "app" / "build.gradle.kts"
DIST = ROOT / "dist"


def run(cmd, cwd=ROOT, check=True):
    print(">", " ".join(str(c) for c in cmd))
    return subprocess.run(cmd, cwd=cwd, check=check, capture_output=True, text=True,
                          shell=False)


def bump(v: str, level: str) -> str:
    major, minor, patch = (int(x) for x in v.split("."))
    if level == "major":
        return f"{major + 1}.0.0"
    if level == "minor":
        return f"{major}.{minor + 1}.0"
    return f"{major}.{minor}.{patch + 1}"


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("level", choices=["patch", "minor", "major"])
    ap.add_argument("--notes", default="", help="notas de la release")
    args = ap.parse_args()

    current = VERSION_FILE.read_text().strip()
    new = bump(current, args.level)

    # 1) VERSION + gradle
    VERSION_FILE.write_text(new + "\n")
    g = GRADLE.read_text(encoding="utf-8")
    code = int(re.search(r"versionCode\s*=\s*(\d+)", g).group(1)) + 1
    g = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {code}", g)
    g = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new}"', g)
    GRADLE.write_text(g, encoding="utf-8")
    print(f"Version {current} -> {new} (versionCode {code})")

    # 2) Build firmado
    run(["cmd", "/c", "gradlew.bat", "assembleRelease"], cwd=ROOT / "android")
    apk_src = ROOT / "android" / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
    if not apk_src.exists():
        print("No se encontro el APK"); return 1

    # 3) dist/
    DIST.mkdir(exist_ok=True)
    apk_dst = DIST / f"Interfon-v{new}.apk"
    apk_dst.write_bytes(apk_src.read_bytes())
    print(f"APK: {apk_dst} ({apk_dst.stat().st_size/1e6:.1f} MB)")

    # 4) git commit + tag + release
    run(["git", "add", "VERSION", "android/app/build.gradle.kts"])
    run(["git", "commit", "-m", f"Release v{new}"])
    run(["git", "tag", f"v{new}"])
    run(["git", "push", "origin", "main", "--tags"])

    notes = args.notes or (
        f"Interfon v{new}\n\n"
        "- APK firmado (versionCode {code}) listo para instalar.\n"
        "- Requiere el servidor Interfon corriendo en el PC (ver README)."
    )
    run(["gh", "release", "create", f"v{new}", str(apk_dst),
         "--title", f"Interfon v{new}", "--notes", notes])
    print(f"\nRelease v{new} publicada con el APK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
