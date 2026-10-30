#!/usr/bin/env python3
"""summarize-sarif.py — dependency-free SARIF summarizer (no external deps).

Reads one or more SARIF files (Semgrep, CodeQL, SpotBugs-with-SARIF, etc.), groups findings
by severity and rule, and prints a ranked summary the agent/judge can act on.

Usage:
    .opencode/scripts/summarize-sarif.py .security/semgrep.sarif [more.sarif ...]
    .opencode/scripts/summarize-sarif.py .security/*.sarif --top 20
"""
import argparse
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
    # SARIF extensions are toolComponent objects directly.
    for ext in run.get("tool", {}).get("extensions", []) or []:
        for r in ext.get("rules", []) or []:
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
    with open(path, "r", encoding="utf-8") as f:
        doc = json.load(f)
    if not isinstance(doc, dict) or doc.get("version") != "2.1.0" or not isinstance(doc.get("runs"), list):
        raise ValueError("expected SARIF 2.1.0 with runs array")
    rows = []
    for run in doc["runs"]:
        if not isinstance(run, dict) or not isinstance(run.get("tool", {}).get("driver"), dict):
            raise ValueError("invalid SARIF run/tool")
        if not isinstance(run.get("results", []), list):
            raise ValueError("invalid SARIF results")
        for invocation in run.get("invocations", []):
            if invocation.get("executionSuccessful") is False or any(
                n.get("level") == "error" for n in invocation.get("toolExecutionNotifications", [])
            ):
                raise ValueError("SARIF reports an incomplete scanner invocation")
        rules = rule_index(run)
        for res in run.get("results", []):
            if not isinstance(res, dict) or not isinstance(res.get("message"), dict):
                raise ValueError("invalid SARIF result/message")
            rid = res.get("ruleId", "<no-rule>")
            rule = rules.get(rid)
            level = rule_level(rule, res)
            if level not in SEV_ORDER:
                raise ValueError("invalid SARIF severity level")
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
    def positive(value):
        number = int(value)
        if number <= 0:
            raise argparse.ArgumentTypeError("must be a positive integer")
        return number

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="+", help="SARIF 2.1.0 reports")
    parser.add_argument("--top", type=positive, default=25, help="positive maximum rows/rules (default: 25)")
    args = parser.parse_args(argv)
    files, top = args.files, args.top

    all_rows = []
    per_file = {}
    failed = False
    for f in files:
        try:
            rows = collect(f)
        except (OSError, ValueError, TypeError, AttributeError, KeyError, IndexError):
            print(f"! unreadable, invalid, or incomplete SARIF: {f}", file=sys.stderr)
            failed = True
            continue
        per_file[f] = rows
        all_rows.extend(rows)

    print("# SARIF summary")
    print(f"Files: {len(per_file)} · Total findings: {len(all_rows)}\n")

    if not per_file:
        print("No valid reports read; coverage unknown.")
        return 2
    if not all_rows:
        if failed:
            print("No candidates in readable reports; coverage incomplete due to report errors.")
        else:
            print("No findings in the supplied valid reports; this does not establish full scan coverage.")
        return 2 if failed else 0

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
    return 2 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
