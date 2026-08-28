"""
JUnit XML test-results parser for DefectDojo 3.2+.

DefectDojo 3.2 dropped its built-in JUnit XML test-import
parser. This file is the upstream patch — a self-contained
parser that ingests JUnit XML (the format MindAnchor's
gradle test task produces) and emits the data DefectDojo's
Test/Engagement model needs.

How to apply
============
1. Copy this file to `dojo/tools/junit/parser.py` in
   the DefectDojo source tree.
2. Add a class entry to `TEST_TYPE_PARSERS` in
   `dojo/settings/unittests.py`:
       ('JUnit Test Report', 'junit', 'JUnit Test Report'),
3. Re-run `./manage.py makemigrations &&
   ./manage.py migrate` in the DefectDojo container.
4. The "JUnit Test Report" entry will then appear in
   the DefectDojo UI's Test type list, and the import
   endpoint will accept JUnit XML uploads.

Test format
===========
JUnit XML is a JUnit3/4-style report with the structure:

  <testsuite name="org.example.MyTest" tests="8"
            failures="0" errors="0" skipped="0" time="0.5">
    <testcase name="case_one" classname="org.example.MyTest"
              time="0.001"/>
    <testcase name="case_two" classname="org.example.MyTest"
              time="0.002">
      <failure message="AssertionError"
              type="AssertionError">stack trace here</failure>
    </testcase>
  </testsuite>

We produce a finding per failing testcase and a summary
finding per testsuite so the DefectDojo engagement shows
both the individual failures and the per-class pass/fail
counts.

What DefectDojo gets
====================
- For each FAILURE / ERROR testcase: a Finding with
  severity High, message = the JUnit failure message,
  file_path = the JUnit XML file path, line = 0 (JUnit
  XML doesn't carry line numbers).
- For each testsuite: a summary Finding with severity
  derived from the failure ratio (Info for 0%, Low for
  <2%, Medium for <10%, High for >=10%).
"""
import io
import zipfile
import re
from defusedxml import ElementTree as ET


def _ratio_to_severity(failures: int, total: int) -> str:
    if total == 0 or failures == 0:
        return "Info"
    pct = failures / total
    if pct >= 0.10:
        return "High"
    if pct >= 0.02:
        return "Medium"
    return "Low"


def get_findings(file, test):
    """Adapter entrypoint DefectDojo's TestParser class calls.

    file: Django File object (uploaded JUnit XML or a .zip of them)
    test: DefectDojo Test model instance
    Returns: list of dicts ready for the Finding model.
    """
    findings = []
    if file.name.lower().endswith(".zip"):
        with zipfile.ZipFile(file, "r") as zf:
            for name in zf.namelist():
                if not name.endswith(".xml"):
                    continue
                with zf.open(name) as member:
                    findings.extend(_parse_junit(member, name))
    else:
        with file.open() as f:
            findings = _parse_junit(f, file.name)
    return findings


def _parse_junit(handle, name: str) -> list[dict]:
    try:
        tree = ET.parse(handle)
    except ET.ParseError:
        return []
    root = tree.getroot()
    if root.tag != "testsuite":
        return []
    tests = int(root.get("tests", 0))
    failures = int(root.get("failures", 0))
    errors = int(root.get("errors", 0))
    skipped = int(root.get("skipped", 0))
    duration = float(root.get("time", 0.0))
    severity = _ratio_to_severity(failures + errors, tests)
    findings = []

    for tc in root.findall("testcase"):
        name_text = tc.get("name", "?")
        class_text = tc.get("classname", "?")
        time_text = float(tc.get("time", 0.0))
        for fail_kind in ("failure", "error"):
            fail_node = tc.find(fail_kind)
            if fail_node is None:
                continue
            message = fail_node.get("message", "(no message)")[:400]
            findings.append(
                {
                    "title": f"Test failure: {name_text}",
                    "description": (fail_node.text or message).strip()[:2000],
                    "severity": "High",
                    "file_path": name,
                    "line": 0,
                    "active": True,
                    "verified": False,
                    "static_finding": False,
                    "dynamic_finding": True,
                    "tag": "junit",
                }
            )
            break
    findings.append(
        {
            "title": f"Test suite: {root.get('name', name)}",
            "description": (
                f"{tests} tests · {failures} failures · "
                f"{errors} errors · {skipped} skipped · "
                f"{duration:.3f}s · severity {severity}"
            ),
            "severity": severity,
            "file_path": name,
            "line": 0,
            "active": False,
            "verified": False,
            "static_finding": False,
            "dynamic_finding": True,
            "tag": "junit",
        }
    )
    return findings
