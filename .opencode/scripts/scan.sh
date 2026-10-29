#!/usr/bin/env bash
# scan.sh — deterministic security scanners for a Spring Boot / Java project.
# Degrades gracefully: a missing tool skips its phase (it never aborts the run) and is reported.
#
# Usage:
#   .opencode/scripts/scan.sh [target-dir]        # default: current dir
#   .opencode/scripts/scan.sh . --quick           # skip the slow OWASP Dependency-Check
#
# Outputs (in <target>/.security/):
#   gitleaks-report.json, semgrep.sarif, spotbugsXml.xml, dependency-check-report.json,
#   scan-summary.md
set -uo pipefail

TARGET="${1:-.}"
QUICK=false
for a in "$@"; do [[ "$a" == "--quick" ]] && QUICK=true; done
TARGET="$(cd "$TARGET" && pwd)"
OUT="$TARGET/.security"
mkdir -p "$OUT"

# Java build tool detection
BUILD_TOOL="none"
[[ -f "$TARGET/mvnw" || -f "$TARGET/pom.xml" ]] && BUILD_TOOL="maven"
[[ -f "$TARGET/gradlew" || -f "$TARGET/build.gradle" || -f "$TARGET/build.gradle.kts" ]] && BUILD_TOOL="gradle"

have() { command -v "$1" >/dev/null 2>&1; }
SUMMARY="$OUT/scan-summary.md"
RAN=0; SKIPPED=()

note() { printf '%s\n' "$*" | tee -a "$SUMMARY"; }
hr()  { note "---"; }

note "# Deterministic scan — $(basename "$TARGET")"
note "Date: $(date -u +%Y-%m-%dT%H:%M:%SZ) · Build tool: $BUILD_TOOL · Quick: $QUICK"
hr

# ── 1. Gitleaks — secrets in code + git history ────────────────────────────
if have gitleaks; then
  note "## Gitleaks (secrets)"
  ( cd "$TARGET" && gitleaks detect --no-banner --redact --report-format json \
      --report-path "$OUT/gitleaks-report.json" ) >/dev/null 2>&1
  if [[ -s "$OUT/gitleaks-report.json" ]] && [[ "$(cat "$OUT/gitleaks-report.json")" != "[]" ]]; then
    COUNT=$(grep -o '"RuleID"' "$OUT/gitleaks-report.json" 2>/dev/null | wc -l | tr -d ' ')
    note "Findings: $COUNT  → see gitleaks-report.json  (a committed secret = P0)"
  else
    note "Findings: 0"
  fi
  hr; RAN=$((RAN+1))
else
  SKIPPED+=("gitleaks (secret scan)"); hr
fi

# ── 2. Semgrep — pattern-based SAST (OWASP/CWE java rules) ─────────────────
if have semgrep; then
  note "## Semgrep (SAST)"
  ( cd "$TARGET" && semgrep --config p/java --config p/owasp-top-ten --config p/security-audit \
      --json --output "$OUT/semgrep.sarif" --sarif --metrics=off src 2>/dev/null ) || true
  if [[ -s "$OUT/semgrep.sarif" ]]; then
    COUNT=$(grep -o '"ruleId"' "$OUT/semgrep.sarif" 2>/dev/null | wc -l | tr -d ' ')
    note "Findings: ~$COUNT  → see semgrep.sarif  (filter by severity; confirm reachability)"
  else
    note "Findings: 0 (or no src/ matched)"
  fi
  hr; RAN=$((RAN+1))
else
  SKIPPED+=("semgrep (SAST)"); hr
fi

# ── 3. SpotBugs + FindSecBugs — Java bytecode security bugs ────────────────
if [[ "$BUILD_TOOL" == "maven" ]] && have java; then
  note "## SpotBugs + FindSecBugs (bytecode)"
  MVN="$TARGET/mvnw"; [[ -x "$MVN" ]] || MVN="mvn"
  ( cd "$TARGET" && "$MVN" -q -DskipTests compile com.github.spotbugs:spotbugs-maven-plugin:check \
      -Dspotbugs.effort=Max -Dspotbugs.threshold=Low ) >/dev/null 2>&1
  BUGS="$TARGET/target/spotbugsXml.xml"
  if [[ -s "$BUGS" ]]; then
    COUNT=$(grep -o '<BugInstance' "$BUGS" | wc -l | tr -d ' ')
    cp "$BUGS" "$OUT/spotbugsXml.xml"
    note "Findings: $COUNT  → see spotbugsXml.xml"
  else
    note "Findings: 0 (or SpotBugs plugin not configured — add spotbugs-maven-plugin)"
  fi
  hr; RAN=$((RAN+1))
elif [[ "$BUILD_TOOL" == "gradle" ]] && have java; then
  note "## SpotBugs (gradle)"
  note "Add the 'com.github.spotbugs' plugin + findsecbugs, then: ./gradlew spotbugsMain"
  note "Skipped automatic run (plugin config not detected)."
  hr; SKIPPED+=("spotbugs (add gradle plugin)")
else
  SKIPPED+=("spotbugs (no maven/gradle build detected)"); hr
fi

# ── 4. OWASP Dependency-Check — known CVEs in dependencies ────────────────
if $QUICK; then
  SKIPPED+=("dependency-check (--quick)")
elif have dependency-check.sh || have dependency-check; then
  note "## OWASP Dependency-Check (CVEs)"
  DC="$(command -v dependency-check.sh || command -v dependency-check)"
  ( cd "$TARGET" && "$DC" --project "$(basename "$TARGET")" --format JSON \
      --out "$OUT" --noupdate ) >/dev/null 2>&1
  RPT="$OUT/dependency-check-report.json"
  if [[ -s "$RPT" ]]; then
    CVES=$(grep -o '"cvssv3\|"cvssv2"' "$RPT" | wc -l | tr -d ' ')
    note "Vulnerabilities referenced: ~$CVES  → see dependency-check-report.json"
  else
    note "No report produced (may need an initial NVD download)."
  fi
  hr; RAN=$((RAN+1))
else
  SKIPPED+=("dependency-check (install OWASP dependency-check)"); hr
fi

# ── Summary ────────────────────────────────────────────────────────────────
note "## Summary"
note "Scanners run: $RAN"
if (( ${#SKIPPED[@]} )); then
  note "Skipped (install to enable):"
  for s in "${SKIPPED[@]}"; do note "  - $s"; done
else
  note "All scanners ran."
fi
note ""
note "Deterministic results still need human/model confirmation of reachability."
note "Feed these into the LLM hunters via: /audit (which cross-checks them)."

echo
echo "Results in: $OUT"
[[ -f "$SUMMARY" ]] && cat "$SUMMARY"
