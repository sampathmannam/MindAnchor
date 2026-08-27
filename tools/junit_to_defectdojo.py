#!/usr/bin/env python3
"""
Convert JUnit XML test results to a JSON format that
DefectDojo's existing "Generic Findings Import" (a.k.a.
Semgrep JSON) parser can ingest, so test results from
MindAnchor's 1346-test JVM suite end up in the DefectDojo
engagement alongside the Semgrep findings.

DefectDojo 3.2 dropped its built-in JUnit XML test-import
parser (see docs/qa/defectdojo-junit-import-regression.md).
The Semgrep JSON parser is generic enough to accept any
record with a "check_id" / "path" / "start" / "end" /
"extra" / "metadata" shape, so we adapt the JUnit testcases
to that schema. Findings = test failures; passing tests are
not represented (DefectDojo test runs are "results", not
"findings" — but the Semgrep parser creates findings, which
is the closest available import path).

Usage:
    tools/junit_to_defectdojo.py \\
        app/build/test-results/testDebugUnitTest \\
        /tmp/ma-junit-defectdojo.json
    curl -u admin:Admin123!@# -X POST \\
        -F "file=@/tmp/ma-junit-defectdojo.json" \\
        -F "scan_type=Semgrep JSON Report" \\
        -F "engagement=1" \\
        http://localhost:8000/api/v2/import-scan/
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable


def iter_junit_files(root: Path) -> Iterable[Path]:
    if root.is_file() and root.suffix == ".xml":
        yield root
        return
    for p in sorted(root.rglob("TEST-*.xml")):
        yield p


def severity_from_failure(failed: int, tests: int) -> str:
    if failed == 0:
        return "Info"
    ratio = failed / max(tests, 1)
    if ratio >= 0.1:
        return "High"
    if ratio >= 0.02:
        return "Medium"
    return "Low"


def convert(xml_path: Path) -> dict | None:
    tree = ET.parse(xml_path)
    suite = tree.getroot()
    if suite.tag != "testsuite":
        return None
    name = suite.get("name", xml_path.stem)
    tests = int(suite.get("tests", 0))
    failures = int(suite.get("failures", 0))
    errors = int(suite.get("errors", 0))
    skipped = int(suite.get("skipped", 0))
    hostname = suite.get("hostname", "")
    duration = float(suite.get("time", 0.0))

    findings: list[dict] = []
    total = failures + errors
    if total > 0:
        # One finding per failing test (DefectDojo shows
        # each as a separate row in the engagement).
        for tc in suite.findall("testcase"):
            failure_node = (
                tc.find("failure")
                if tc.find("failure") is not None
                else tc.find("error")
            )
            if failure_node is None:
                continue
            tc_class = tc.get("classname", "?")
            tc_name = tc.get("name", "?")
            tc_time = float(tc.get("time", 0.0))
            message = failure_node.get("message", "(no message)")
            findings.append(
                {
                    "check_id": f"junit-failure/{tc_class}.{tc_name}",
                    "path": xml_path.as_posix(),
                    "start": {"line": 0, "col": 0},
                    "end": {"line": 0, "col": 0},
                    "extra": {
                        "message": message,
                        "severity": "High",
                        "metadata": {
                            "test_class": tc_class,
                            "test_name": tc_name,
                            "duration_s": tc_time,
                            "suite": name,
                            "hostname": hostname,
                            "source": "JUnit XML → Semgrep JSON shim",
                        },
                    },
                }
            )
    # Always emit a "test summary" finding so the
    # engagement shows the test stats even when all green.
    findings.append(
        {
            "check_id": f"junit-summary/{name}",
            "path": xml_path.as_posix(),
            "start": {"line": 0, "col": 0},
            "end": {"line": 0, "col": 0},
            "extra": {
                "message": (
                    f"{tests} tests, {failures} failures, "
                    f"{errors} errors, {skipped} skipped, "
                    f"{duration:.3f}s, severity {severity_from_failure(failures, tests)}"
                ),
                "severity": severity_from_failure(failures, tests),
                "metadata": {
                    "tests": tests,
                    "failures": failures,
                    "errors": errors,
                    "skipped": skipped,
                    "duration_s": duration,
                    "suite": name,
                    "hostname": hostname,
                    "source": "JUnit XML → Semgrep JSON shim",
                },
            },
        }
    )
    return {
        "results": findings,
        "errors": [],
        "paths": {"scanned": [str(xml_path)]},
        "version": "0.1",
    }


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "usage: junit_to_defectdojo.py <junit-results-dir-or-file>"
            " <output.json>",
            file=sys.stderr,
        )
        return 2
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2])
    if not src.exists():
        print(f"not found: {src}", file=sys.stderr)
        return 2
    out = {"results": [], "errors": [], "paths": {"scanned": []}, "version": "0.1"}
    total_tests = total_fail = total_err = total_skip = 0
    suite_count = 0
    for xml in iter_junit_files(src):
        d = convert(xml)
        if d is None:
            continue
        out["results"].extend(d["results"])
        out["paths"]["scanned"].extend(d["paths"]["scanned"])
        # Aggregate totals from the summary finding.
        for r in d["results"]:
            md = r["extra"]["metadata"]
            if "tests" in md:
                suite_count += 1
                total_tests += int(md["tests"])
                total_fail += int(md["failures"])
                total_err += int(md["errors"])
                total_skip += int(md["skipped"])
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(out, indent=2))
    print(
        f"converted {suite_count} suites → {len(out['results'])} findings"
        f" ({total_tests} tests, {total_fail} failures,"
        f" {total_err} errors, {total_skip} skipped)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
