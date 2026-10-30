#!/usr/bin/env bash
# Trusted OWN project only: never run on a rival checkout.
# Usage: scan.sh [target] [--quick] [--allow-build] [--allow-network]
# Exit 0: complete clean coverage; 1: candidates with complete coverage;
#      2: incomplete/error, including skipped required coverage or no source tree.
# Fresh reports: <target>/.security/scans/<run-id>/{scan-status.json,scan-summary.md,...}
# Requires Python 3.9+; build opt-in currently supports Maven + installed SpotBugs CLI.
set -euo pipefail
exec python3 - "${BASH_SOURCE[0]}" "$@" <<'PY'
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=(
        'Scan only this trusted tooling project root or its backend directory, never rival checkouts. '
        'Exit 0: complete clean coverage; 1: candidates with complete coverage; '
        '2: incomplete/error, including skipped required coverage. No installs.'))
    parser.add_argument('target', nargs='?', default='.')
    parser.add_argument('--quick', action='store_true', help='skip dependency-check (incomplete coverage)')
    parser.add_argument('--allow-build', action='store_true', help=(
        'allow trusted wrappers/compiler plugins to EXECUTE CODE; offline unless --allow-network'))
    parser.add_argument('--allow-network', action='store_true', help=(
        'allow Semgrep registry rules, dependency DB updates, and build dependency downloads'))
    args = parser.parse_args(sys.argv[2:])
    root = Path(sys.argv[1]).resolve().parents[2]
    try:
        target = Path(args.target).resolve(strict=True)
        if not target.is_dir() or target not in (root, root / 'backend'):
            parser.error('target must be this trusted project root or its backend directory')
        security = target / '.security'
        scans = security / 'scans'
        for directory in (security, scans):
            if directory.is_symlink():
                parser.error('refusing symlinked report directory')
            directory.mkdir(mode=0o700, exist_ok=True)
            if not directory.is_dir() or directory.resolve() != directory:
                parser.error('report directory escapes trusted target')
        out = Path(tempfile.mkdtemp(prefix=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-'), dir=scans))
    except OSError:
        parser.error('target/report directory is missing, unreadable, or unsafe')

    phases = []

    def record(name, status, reason, count=None, report=None):
        item = dict(scanner=name, status=status, reason=reason)
        if count is not None:
            item['candidate_count'] = count
        if report is not None:
            item['report'] = report.name
        phases.append(item)

    def run(command, cwd=target):
        # Tools may print secrets even on failure. Never echo or retain raw logs.
        try:
            return subprocess.run(command, cwd=cwd, stdin=subprocess.DEVNULL,
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                  check=False).returncode
        except OSError:
            return -1

    def load_report(path):
        if path.is_symlink() or not path.is_file() or path.resolve().parent != out:
            raise ValueError('missing/unsafe report')
        with path.open(encoding='utf-8') as handle:
            return json.load(handle)

    def json_phase(name, command, report, validate, findings_exit=False):
        code = run(command)
        if code not in ((0, 1) if findings_exit else (0,)):
            record(name, 'error', f'scanner failed (exit {code}); output suppressed')
            return
        try:
            count = validate(load_report(report))
            if findings_exit and ((code == 1) != (count > 0)):
                raise ValueError('exit/report mismatch')
        except (OSError, ValueError, TypeError, KeyError, AttributeError):
            record(name, 'error', 'missing, malformed, unsafe, or inconsistent report')
            return
        record(name, 'findings' if count else 'clean', 'candidates require independent validation', count, report)

    backend = target / 'backend' if (target / 'backend' / 'src' / 'main').is_dir() else target
    java_source = backend / 'src' / 'main'
    sources = list(dict.fromkeys(p for p in (java_source, target / 'frontend' / 'src', target / 'src') if p.is_dir()))
    if any(not p.resolve().is_relative_to(target) for p in sources):
        record('source-discovery', 'error', 'source root escapes trusted target')
        sources = []
    sources = [p for p in sources if not any(p != other and p.is_relative_to(other) for other in sources)]

    def gitleaks_count(doc):
        if not isinstance(doc, list) or any(not isinstance(row, dict) or 'RuleID' not in row for row in doc):
            raise ValueError('expected gitleaks array')
        return len(doc)

    if shutil.which('gitleaks'):
        report = out / 'gitleaks-report.json'
        json_phase('gitleaks', ['gitleaks', 'detect', '--source', str(target), '--no-banner', '--redact',
                               '--report-format', 'json', '--report-path', str(report)],
                   report, gitleaks_count, findings_exit=True)
    else:
        record('gitleaks', 'skipped', 'tool unavailable')

    def sarif_count(doc):
        if not isinstance(doc, dict) or doc.get('version') != '2.1.0' or not isinstance(doc.get('runs'), list) or not doc['runs']:
            raise ValueError('expected SARIF 2.1.0 runs')
        count = 0
        for entry in doc['runs']:
            if not isinstance(entry, dict) or not isinstance(entry.get('tool', {}).get('driver'), dict):
                raise ValueError('invalid SARIF run')
            results = entry.get('results', [])
            if not isinstance(results, list) or any(not isinstance(r, dict) for r in results):
                raise ValueError('invalid results')
            invocations = entry.get('invocations', [])
            if not isinstance(invocations, list) or any(not isinstance(i, dict) or i.get('executionSuccessful') is False for i in invocations):
                raise ValueError('failed invocation')
            if any(n.get('level') == 'error' for i in invocations for n in i.get('toolExecutionNotifications', [])):
                raise ValueError('scanner error')
            count += len(results)
        return count

    if not sources:
        record('semgrep', 'not_applicable', 'no backend/src/main, frontend/src, or traditional src detected')
    elif not args.allow_network:
        record('semgrep', 'skipped', 'registry rules require --allow-network')
    elif not shutil.which('semgrep'):
        record('semgrep', 'skipped', 'tool unavailable')
    else:
        report = out / 'semgrep.sarif'
        json_phase('semgrep', ['semgrep', 'scan', '--config', 'p/java', '--config', 'p/typescript',
                              '--config', 'p/react', '--sarif', '--output', str(report),
                              '--metrics=off', '--disable-version-check', *map(str, sources)], report, sarif_count)

    if not java_source.resolve().is_relative_to(target):
        record('spotbugs', 'error', 'Java source root escapes trusted target')
    elif not java_source.is_dir():
        record('spotbugs', 'not_applicable', 'no Java backend src/main detected')
    elif not args.allow_build:
        record('spotbugs', 'skipped', 'requires --allow-build; wrappers/compiler plugins execute trusted code')
    elif not (backend / 'pom.xml').is_file():
        record('spotbugs', 'skipped', 'automatic bytecode build supports Maven only; Gradle plugin configuration not inferred')
    elif not shutil.which('spotbugs') or not shutil.which('java'):
        record('spotbugs', 'skipped', 'requires installed SpotBugs CLI and Java; FindSecBugs not assumed configured')
    else:
        wrapper = backend / 'mvnw'
        maven = str(wrapper) if wrapper.is_file() and os.access(wrapper, os.X_OK) else shutil.which('mvn')
        if not maven:
            record('spotbugs', 'skipped', 'Maven unavailable')
        else:
            # Explicit build consent above; clean prevents stale bytecode analysis.
            code = run([maven, *([] if args.allow_network else ['--offline']), '-B', '-DskipTests', 'clean', 'compile'], backend)
            classes = backend / 'target' / 'classes'
            if code != 0 or not classes.is_dir() or not classes.resolve().is_relative_to(backend):
                record('spotbugs', 'error', 'trusted compilation failed or bytecode missing/unsafe')
            else:
                report = out / 'spotbugs.xml'
                code = run(['spotbugs', '-textui', '-xml:withMessages', '-output', str(report), str(classes)])
                try:
                    if code != 0 or report.is_symlink() or report.resolve().parent != out:
                        raise ValueError('scan failed')
                    tree = ET.parse(report)
                    if tree.getroot().tag != 'BugCollection' or tree.findall('.//Error') or tree.findall('.//MissingClass'):
                        raise ValueError('incomplete bytecode analysis')
                    count = len(tree.findall('.//BugInstance'))
                    record('spotbugs', 'findings' if count else 'clean', 'SpotBugs only; FindSecBugs coverage not claimed', count, report)
                except (OSError, ValueError, ET.ParseError):
                    record('spotbugs', 'error', 'failed, incomplete, missing, or invalid XML report')

    def dependency_count(doc):
        if not isinstance(doc, dict) or not isinstance(doc.get('dependencies'), list) or not isinstance(doc.get('scanInfo'), dict):
            raise ValueError('invalid dependency report')
        if doc['scanInfo'].get('analysisExceptions'):
            raise ValueError('incomplete analysis')
        count = 0
        for dependency in doc['dependencies']:
            if not isinstance(dependency, dict) or not isinstance(dependency.get('vulnerabilities', []), list):
                raise ValueError('invalid dependency')
            count += len(dependency.get('vulnerabilities', []))
        return count

    dc = shutil.which('dependency-check.sh') or shutil.which('dependency-check')
    if not sources:
        record('dependency-check', 'not_applicable', 'no supported source tree detected')
    elif args.quick:
        record('dependency-check', 'skipped', '--quick requested; dependency coverage incomplete')
    elif not args.allow_network:
        record('dependency-check', 'skipped', 'requires --allow-network for DB freshness; --noupdate cannot establish current CVE coverage')
    elif not dc:
        record('dependency-check', 'skipped', 'tool unavailable')
    else:
        report = out / 'dependency-check-report.json'
        json_phase('dependency-check', [dc, '--project', target.name, '--scan', str(target),
                                      '--exclude', str(security / '**'), '--format', 'JSON', '--out', str(out)],
                   report, dependency_count)

    incomplete = not sources or any(p['status'] in ('skipped', 'error') for p in phases)
    candidates = sum(p.get('candidate_count', 0) for p in phases)
    code = 2 if incomplete else (1 if candidates else 0)
    status = dict(schema_version=1, target=str(target), run_id=out.name,
                  generated_at=datetime.now(timezone.utc).isoformat(), exit_code=code,
                  coverage_complete=not incomplete, candidate_count=candidates, scanners=phases)
    # Exclusive creation also refuses pre-existing symlinks, including tool-created ones.
    with (out / 'scan-status.json').open('x', encoding='utf-8') as handle:
        handle.write(json.dumps(status, indent=2) + '\n')
    summary = ['# Deterministic scanner candidates', '',
               f'Coverage: {"incomplete" if incomplete else "complete"}; candidates: {candidates}; exit: {code}', '']
    for phase in phases:
        summary.append(f'- {phase["scanner"]}: {phase["status"]} — {phase["reason"]}' +
                       (f' ({phase["candidate_count"]} candidates)' if 'candidate_count' in phase else ''))
    summary += ['', 'Skipped/error is not clean. Candidates require independent validation; no automatic priority/verdict.',
                'Dependency-Check uses normal DB freshness updates only with --allow-network; DBs can still lag disclosures.']
    text = '\n'.join(summary) + '\n'
    with (out / 'scan-summary.md').open('x', encoding='utf-8') as handle:
        handle.write(text)
    print(text, end='')
    print(f'Results in: {out}')
    return code


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, TypeError, AttributeError):
        print('Scan incomplete: unable to process scanner output or write reports.', file=sys.stderr)
        sys.exit(2)
PY
