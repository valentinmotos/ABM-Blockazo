"""Hermetic scanner contract tests: only fixture executables, no builds/network."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


PROJECT = Path(__file__).resolve().parents[2]
BASH = shutil.which('bash')
SUMMARY = PROJECT / '.opencode/scripts/summarize-sarif.py'


class ScannerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='scanner-tests-', dir='/tmp/opencode')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'trusted'
        scripts = self.root / '.opencode/scripts'
        scripts.mkdir(parents=True)
        self.scan = scripts / 'scan.sh'
        shutil.copyfile(PROJECT / '.opencode/scripts/scan.sh', self.scan)
        self.bin = self.base / 'bin'
        self.bin.mkdir()
        (self.bin / 'python3').symlink_to(sys.executable)
        self.env = {**os.environ, 'PATH': str(self.bin), 'CALLS': str(self.base / 'calls.jsonl')}
        self.frontend()

    def frontend(self):
        (self.root / 'frontend/src').mkdir(parents=True, exist_ok=True)

    def backend(self):
        (self.root / 'backend/src/main').mkdir(parents=True)
        (self.root / 'backend/pom.xml').write_text('<project/>')

    def stub(self, name, body):
        path = self.bin / name
        path.write_text(f'#!{sys.executable}\nimport json, os, sys\nfrom pathlib import Path\n'
                        'with open(os.environ["CALLS"], "a") as f:\n'
                        '    f.write(json.dumps([Path(sys.argv[0]).name, *sys.argv[1:]]) + "\\n")\n' + body)
        path.chmod(0o700)

    def gitleaks(self, mode='clean'):
        self.env['LEAK_MODE'] = mode
        self.stub('gitleaks', '''
args = sys.argv[1:]
report = Path(args[args.index('--report-path') + 1])
mode = os.environ['LEAK_MODE']
print('RAW_SECRET_MUST_NOT_ESCAPE')
if mode != 'missing':
    report.write_text(json.dumps([{'RuleID': 'fixture'}] if mode == 'findings' else {} if mode == 'invalid' else []))
sys.exit(1 if mode == 'findings' else 3 if mode == 'error' else 0)
''')

    def other_scanners(self):
        self.stub('semgrep', '''
args = sys.argv[1:]
Path(args[args.index('--output') + 1]).write_text(json.dumps({
    'version': '2.1.0', 'runs': [{'tool': {'driver': {'name': 'fixture'}}, 'results': []}]}))
''')
        self.stub('dependency-check', '''
args = sys.argv[1:]
(Path(args[args.index('--out') + 1]) / 'dependency-check-report.json').write_text(
    json.dumps({'scanInfo': {}, 'dependencies': []}))
''')

    def run_scan(self, *args):
        result = subprocess.run([BASH, str(self.scan), *map(str, args)], cwd=self.root,
                                env=self.env, capture_output=True, text=True)
        self.assertNotIn('RAW_SECRET_MUST_NOT_ESCAPE', result.stdout + result.stderr)
        return result

    def reports(self):
        return list((self.root / '.security/scans').glob('*/scan-status.json'))

    def status(self):
        reports = self.reports()
        self.assertTrue(reports)
        return json.loads(max(reports, key=lambda p: p.stat().st_mtime_ns).read_text())

    def phase(self, name):
        return next(p for p in self.status()['scanners'] if p['scanner'] == name)

    def calls(self):
        path = Path(self.env['CALLS'])
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def test_missing_tools_incomplete_and_flags_before_target(self):
        result = self.run_scan('--quick', '.')
        self.assertEqual(result.returncode, 2)
        self.assertFalse(self.status()['coverage_complete'])
        self.assertEqual(self.phase('gitleaks')['status'], 'skipped')
        self.assertNotIn('candidate_count', self.phase('gitleaks'))

    def test_invalid_targets_and_unknown_args_fail_without_reports(self):
        for args in [('missing',), (self.base,), ('--surprise',), ('.', '.')]:
            with self.subTest(args=args):
                self.assertEqual(self.run_scan(*args).returncode, 2)
        self.assertFalse(self.reports())

    def test_help_does_not_require_tools(self):
        result = self.run_scan('--help')
        self.assertEqual(result.returncode, 0)
        for text in ('--allow-build', '--allow-network', '--quick', 'EXECUTE CODE'):
            self.assertIn(text, result.stdout)
        self.assertFalse(self.reports())

    def test_symlinked_output_directory_refused(self):
        outside = self.base / 'outside'
        outside.mkdir()
        (self.root / '.security').symlink_to(outside, target_is_directory=True)
        self.assertEqual(self.run_scan().returncode, 2)
        self.assertEqual(list(outside.iterdir()), [])

    def test_symlinked_scans_directory_refused(self):
        outside = self.base / 'outside'
        outside.mkdir()
        (self.root / '.security').mkdir()
        (self.root / '.security/scans').symlink_to(outside, target_is_directory=True)
        self.assertEqual(self.run_scan().returncode, 2)
        self.assertEqual(list(outside.iterdir()), [])

    def test_gitleaks_error_and_invalid_report_never_clean(self):
        for mode in ('error', 'invalid', 'missing'):
            with self.subTest(mode=mode):
                self.gitleaks(mode)
                self.assertEqual(self.run_scan().returncode, 2)
                self.assertEqual(self.phase('gitleaks')['status'], 'error')
                self.assertNotIn('candidate_count', self.phase('gitleaks'))
        self.assertTrue(all('--redact' in call for call in self.calls()))

    def test_stale_output_is_not_reused_and_runs_never_overwrite(self):
        self.gitleaks()
        self.run_scan()
        old = self.reports()[0]
        old_content = old.read_text()
        (self.root / '.security/gitleaks-report.json').write_text('[]')
        self.env['LEAK_MODE'] = 'missing'
        self.run_scan()
        self.assertEqual(len(self.reports()), 2)
        self.assertEqual(old.read_text(), old_content)
        self.assertEqual(self.phase('gitleaks')['status'], 'error')

    def test_complete_frontend_clean_and_candidates_exit(self):
        self.gitleaks()
        self.other_scanners()
        self.assertEqual(self.run_scan('--allow-network').returncode, 0)
        self.assertTrue(self.status()['coverage_complete'])
        self.env['LEAK_MODE'] = 'findings'
        self.assertEqual(self.run_scan('--allow-network').returncode, 1)
        self.assertEqual(self.status()['candidate_count'], 1)
        dc = next(c for c in self.calls() if c[0] == 'dependency-check')
        self.assertEqual(dc[dc.index('--scan') + 1], str(self.root))
        self.assertNotIn('--noupdate', dc)

    def test_semgrep_and_dependency_failures_are_errors(self):
        self.gitleaks()
        self.stub('semgrep', 'sys.exit(2)\n')
        self.stub('dependency-check', 'sys.exit(3)\n')
        self.assertEqual(self.run_scan('--allow-network').returncode, 2)
        for scanner in ('semgrep', 'dependency-check'):
            self.assertEqual(self.phase(scanner)['status'], 'error')
            self.assertNotIn('candidate_count', self.phase(scanner))

    def test_backend_subdirectory_is_accepted(self):
        self.backend()
        result = self.run_scan('backend', '--quick')
        self.assertEqual(result.returncode, 2)
        reports = list((self.root / 'backend/.security/scans').glob('*/scan-status.json'))
        self.assertEqual(len(reports), 1)
        self.assertEqual(json.loads(reports[0].read_text())['target'], str(self.root / 'backend'))

    def test_source_escape_never_triggers_opt_in_build(self):
        outside = self.base / 'outside'
        (outside / 'src/main').mkdir(parents=True)
        (outside / 'pom.xml').write_text('<project/>')
        (self.root / 'backend').symlink_to(outside, target_is_directory=True)
        self.stub('mvn', 'sys.exit(9)\n')
        self.stub('java', '')
        self.stub('spotbugs', '')
        self.assertEqual(self.run_scan('--allow-build').returncode, 2)
        self.assertEqual(self.phase('spotbugs')['status'], 'error')
        self.assertEqual(self.calls(), [])

    def test_no_build_or_network_without_consent_and_monorepo_sources(self):
        self.backend()
        self.gitleaks()
        self.other_scanners()
        self.stub('mvn', 'raise SystemExit("should not run")\n')
        self.stub('spotbugs', 'raise SystemExit("should not run")\n')
        self.stub('java', '')
        self.assertEqual(self.run_scan().returncode, 2)
        self.assertEqual([c[0] for c in self.calls()], ['gitleaks'])
        self.assertEqual(self.phase('spotbugs')['status'], 'skipped')
        self.assertEqual(self.run_scan('--allow-network').returncode, 2)
        semgrep = next(c for c in self.calls() if c[0] == 'semgrep')
        self.assertIn(str(self.root / 'backend/src/main'), semgrep)
        self.assertIn(str(self.root / 'frontend/src'), semgrep)
        self.assertIn('--sarif', semgrep)
        self.assertNotIn('--json', semgrep)
        self.assertNotIn('mvn', [c[0] for c in self.calls()])

    def test_opt_in_build_is_offline_and_spotbugs_only(self):
        self.backend()
        self.stub('java', '')
        self.stub('mvn', "(Path.cwd() / 'target/classes').mkdir(parents=True)\n")
        self.stub('spotbugs', '''
Path(sys.argv[sys.argv.index('-output') + 1]).write_text('<BugCollection/>')
''')
        self.assertEqual(self.run_scan('--allow-build').returncode, 2)
        maven = next(c for c in self.calls() if c[0] == 'mvn')
        self.assertIn('--offline', maven)
        self.assertIn('clean', maven)
        self.assertEqual(self.phase('spotbugs')['status'], 'clean')
        self.assertIn('FindSecBugs coverage not claimed', self.phase('spotbugs')['reason'])

    def test_no_source_is_not_clean(self):
        shutil.rmtree(self.root / 'frontend')
        self.gitleaks()
        self.assertEqual(self.run_scan().returncode, 2)
        self.assertEqual(self.phase('semgrep')['status'], 'not_applicable')
        self.assertFalse(self.status()['coverage_complete'])


class SarifTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='sarif-tests-', dir='/tmp/opencode')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def report(self, name='report.sarif', results=None):
        path = self.root / name
        path.write_text(json.dumps({'version': '2.1.0', 'runs': [{
            'tool': {'driver': {'name': 'fixture', 'rules': [
                {'id': 'test', 'defaultConfiguration': {'level': 'error'}}]}},
            'results': results or []}]}))
        return path

    def summarize(self, *args):
        return subprocess.run([sys.executable, str(SUMMARY), *map(str, args)],
                              capture_output=True, text=True)

    def test_top_is_not_filename_and_rule_default_is_preserved(self):
        path = self.report(results=[{'ruleId': 'test', 'message': {'text': 'fixture'}}])
        for args in ((path, '--top', '1'), ('--top', '1', path)):
            result = self.summarize(*args)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('[error', result.stdout)
            self.assertEqual(result.stderr, '')

    def test_top_requires_positive_integer(self):
        for value in ('0', '-1', 'nope'):
            self.assertNotEqual(self.summarize(self.report(), '--top', value).returncode, 0)

    def test_invalid_unreadable_and_no_reports_are_not_clean(self):
        invalid = self.root / 'invalid.sarif'
        for text in ('oops', '{}', '{"version":"2.1.0","runs":{}}',
                     '{"version":"2.1.0","runs":[{}]}'):
            invalid.write_text(text)
            result = self.summarize(invalid)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn('No findings', result.stdout)
        self.assertNotEqual(self.summarize(self.root / 'missing').returncode, 0)
        self.assertNotEqual(self.summarize().returncode, 0)

    def test_valid_clean_report_and_partial_failure(self):
        valid = self.report()
        result = self.summarize(valid)
        self.assertEqual(result.returncode, 0)
        self.assertIn('No findings in the supplied valid reports', result.stdout)
        result = self.summarize(valid, self.root / 'missing')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('No findings', result.stdout)
        self.assertIn('coverage incomplete', result.stdout)

    def test_failed_scanner_invocation_is_not_clean(self):
        path = self.report()
        doc = json.loads(path.read_text())
        doc['runs'][0]['invocations'] = [{'executionSuccessful': False}]
        path.write_text(json.dumps(doc))
        result = self.summarize(path)
        self.assertEqual(result.returncode, 2)
        self.assertNotIn('No findings', result.stdout)


if __name__ == '__main__':
    unittest.main()
