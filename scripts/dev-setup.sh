#!/usr/bin/env bash
# Prepare this checkout without starting services or reading local credentials.
set -euo pipefail

cd "$(dirname "$0")/.."
check=false
warm=false
for argument in "$@"; do
  case "$argument" in
    --check) check=true ;;
    --warm-up) warm=true ;;
    *) echo "usage: $0 [--check | --warm-up]" >&2; exit 2 ;;
  esac
done
if $check && $warm; then
  echo "Choose --check or --warm-up, not both" >&2
  exit 2
fi
fail() { echo "Setup: $*" >&2; exit 1; }

command -v git >/dev/null || fail "install Git and make it available on PATH"
java_command=java
if [[ -n "${JAVA_HOME:-}" ]]; then java_command="$JAVA_HOME/bin/java"; fi
java_version=$("$java_command" -version 2>&1) || fail "install JDK 25 and set JAVA_HOME or PATH"
[[ "$java_version" =~ version[[:space:]]+\"25[.\"] ]] \
  || fail "JDK 25 is required; set JAVA_HOME or PATH to JDK 25"
command -v python3 >/dev/null || fail "install Python 3 with venv support"
python3 - <<'PYVENV' || fail "install python3-venv or virtualenv for Python 3"
import shutil
import subprocess
import sys
import tempfile
with tempfile.TemporaryDirectory(prefix="invoice-watcher-venv-") as directory:
    result = subprocess.run([sys.executable, "-m", "venv", directory],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if result.returncode and shutil.which("virtualenv"):
        result = subprocess.run(["virtualenv", "--quiet", directory],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    raise SystemExit(result.returncode)
PYVENV
command -v docker >/dev/null || fail "install Docker with Compose and allow your user access"
docker info >/dev/null 2>&1 || fail "start Docker and allow your user access to its daemon"
docker compose version >/dev/null 2>&1 || fail "install the Docker Compose plugin"
[[ -x ./gradlew ]] || fail "restore the executable Gradle wrapper in this checkout"

if $check; then
  echo "Setup prerequisites available; no build or verification has run."
  exit 0
fi
bash scripts/worktree.sh link-config
./gradlew :frontend:frontendInstall
if $warm; then ./gradlew classes testClasses; fi
echo "Setup complete. Run scripts/verify.sh to verify this checkout."
