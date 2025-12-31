#!/usr/bin/env bash
set -euo pipefail

# Determine repo root relative to this script
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Optional thread count argument (default: 4)
THREAD_COUNT="${1:-4}"

echo "Running spl.lae.Main on Examples/example*.json and comparing outputs..."

# Try to compile if Maven is available; otherwise rely on existing build outputs
CP="target/classes"
OUT_DIR="${ROOT_DIR}/script_output"

# Ensure output directory exists and is empty on each run
mkdir -p "$OUT_DIR"
find "$OUT_DIR" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
if command -v mvn >/dev/null 2>&1; then
  echo "Compiling project with Maven (skip tests)..."
  mvn -q -DskipTests compile
  echo "Building runtime classpath with Maven dependency plugin..."
  MVN_CP=$(mvn -q -DincludeScope=runtime dependency:build-classpath -Dmdep.outputFile=/dev/stdout | tail -n1)
  if [[ -n "$MVN_CP" ]]; then
    CP="target/classes:${MVN_CP}"
  fi
else
  echo "Maven not found; using existing classes in target/classes if available."
fi

matches=0
failures=0

declare -a reports=()

for file in Examples/example*.json; do
  # Skip if glob doesn't match any files
  [[ -e "$file" ]] || continue

  fname="${file##*/}"
  index="${fname#example}"
  index="${index%.json}"

  out_file="${OUT_DIR}/output${index}test.json"
  diff_file="${OUT_DIR}/output${index}.diff"

  echo "Processing ${fname} -> ${out_file}"

  set +e
  java -cp "$CP" spl.lae.Main "$THREAD_COUNT" "$file" "$out_file"
  rc=$?
  set -e

  if [[ $rc -ne 0 ]]; then
    reports+=("example${index}: RUN FAILED (exit ${rc})")
    failures=$((failures+1))
    continue
  fi

  if diff -u -w --strip-trailing-cr "Examples/out${index}.json" "$out_file" > "$diff_file"; then
    reports+=("example${index}: MATCH")
    matches=$((matches+1))
    rm -f "$diff_file"
  else
    reports+=("example${index}: DIFF -> ${diff_file}")
  fi

done

echo
echo "Summary:"
for r in "${reports[@]}"; do
  echo " - ${r}"
done

echo "Matches: ${matches}"
echo "Failures: ${failures}"
