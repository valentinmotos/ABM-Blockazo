#!/usr/bin/env python3
"""summarize-sarif.py — dependency-free SARIF summarizer (no external deps).

Reads one or more SARIF files (Semgrep, CodeQL, SpotBugs-with-SARIF, etc.), groups findings
by severity and rule, and prints a ranked summary the agent/judge can act on.

Usage:
    .opencode/scripts/summarize-sarif.py .security/semgrep.sarif [more.sarif ...]
    .opencode/scripts/summarize-sarif.py .security/*.sarif --top 20
"""
import json
import sys
from collections import Counter, defaultdict

# SARIF level -> our severity ordering (higher = worse)
SEV_ORDER = {"error": 0, "warning": 1, "note": 2, "none": 3}


def rule_index(run):
    """Map ruleId -> rule object (for level/description)."""
    idx = {}
    for section in ("rules",):
        driver = run.get("tool", {}).get("driver", {})
        for r in driver.get(section, []) or []:
            idx[r.get("id")] = r
    # extended driver (taxonomies/toolComponent) — best effort
    for ext in run.get("tool", {}).get("extensions", []) or []:
        for r in ext.get("toolComponent", {}).get("rules", []) or []:
            idx.setdefault(r.get("id"), r)
    return idx


def rule_level(rule, result):
    """Resolve a result's severity from the result, then the rule."""
    lvl = result.get("level")
    if lvl:
        return lvl
    if rule:
        lvl = (rule.get("defaultConfiguration") or {}).get("level")
        if lvl:
            return lvl
    return "warning"


def collect(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        doc = json.load(f)
    rows = []
    for run in doc.get("runs", []) or []:
        rules = rule_index(run)
        for res in run.get("results", []) or []:
            rid = res.get("ruleId", "<no-rule>")
            rule = rules.get(rid)
            level = rule_level(rule, res)
            loc = "?"
            msg = (res.get("message") or {}).get("text", "").strip().splitlines()
            msg = msg[0] if msg else ""
            locs = res.get("locations") or []
            if locs:
                pl = locs[0].get("physicalLocation") or {}
                art = pl.get("artifactLocation") or {}
                region = pl.get("region") or {}
                loc = f"{art.get('uri', '?')}:{region.get('startLine', '?')}"
            rows.append({"rule": rid, "level": level, "loc": loc, "msg": msg})
    return rows


def main(argv):
    files = [a for a in argv if not a.startswith("--")]
    top = 25
    if "--top" in argv:
        try:
            top = int(argv[argv.index("--top") + 1])
        except (IndexError, ValueError):
            pass
    if not files:
        print(__doc__)
        return 1

    all_rows = []
    per_file = {}
    for f in files:
        try:
            rows = collect(f)
        except (OSError, json.JSONDecodeError) as e:
            print(f"! could not read {f}: {e}", file=sys.stderr)
            continue
        per_file[f] = rows
        all_rows.extend(rows)

    print("# SARIF summary")
    print(f"Files: {len(per_file)} · Total findings: {len(all_rows)}\n")

    if not all_rows:
        print("No findings. (Deterministic scanners are one signal — pair with the LLM hunters.)")
        return 0

    by_sev = Counter(r["level"] for r in all_rows)
    print("## By severity")
    for lvl in sorted(by_sev, key=lambda l: SEV_ORDER.get(l, 9)):
        print(f"  {lvl:8} {by_sev[lvl]}")
    print()

    print("## Top rules")
    for rule, n in Counter(r["rule"] for r in all_rows).most_common(top):
        print(f"  {n:4}  {rule}")
    print()

    print("## Findings (worst first)")
    rows = sorted(all_rows, key=lambda r: SEV_ORDER.get(r["level"], 9))
    for r in rows[:top]:
        msg = r["msg"][:110]
        print(f"  [{r['level']:7}] {r['loc']}")
        print(f"            {r['rule']}: {msg}")
    if len(rows) > top:
        print(f"  ... {len(rows) - top} more (use --top N)")
    print()
    print("Confirm reachability before reporting — a scanner hit is a candidate, not a verdict.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
