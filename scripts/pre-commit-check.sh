#!/usr/bin/env bash
# Optional local pre-commit: copy to .git/hooks/pre-commit and chmod +x
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
VM="$ROOT/android/app/src/main/java/com/manhunt/app/viewmodel/GameViewModel.kt"

if [[ ! -f "$VM" ]]; then
  echo "pre-commit: GameViewModel.kt missing"
  exit 1
fi
if grep -q "TEMPORARY PLACEHOLDER\|SEE_ARTIFACT" "$VM"; then
  echo "pre-commit: GameViewModel.kt is a stub — aborting commit"
  exit 1
fi
for sym in "fun createRoom" "fun attemptCatch" "class GameViewModel"; do
  grep -q "$sym" "$VM" || { echo "pre-commit: missing $sym"; exit 1; }
done
echo "pre-commit: structure checks passed"
