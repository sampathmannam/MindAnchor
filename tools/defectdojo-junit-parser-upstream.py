# MindAnchor upstream patch — see tools/qa/defectdojo-junit-import-regression.md.
#
# How to apply:
#   1. Copy to dojo/tools/junit/parser.py in the DefectDojo source tree.
#   2. Re-run migrations (the factory in dojo/tools/factory.py
#      auto-registers any class named `<tool>parser` from
#      `dojo.tools.<tool>.parser`).
#   3. The "JUnit Test Report" entry appears in the DefectDojo
#      UI's Test type dropdown, and the import endpoint
#      accepts JUnit XML uploads.
import zipfile
from xml.etree import ElementTree as ET  # nosemgrep: python.lang.security.use-defused-xml.use-defused-xml -- must mirror DefectDojo's upstream import verbatim; see header


def _ratio_to_severity(failures, total):
    if total == 0 or failures == 0:
        return "Info"
    pct = failures / total
    if pct >= 0.10:
        return "High"
    if pct >= 0.02:
        return "Medium"
    return "Low"


def _parse_junit(handle, name):
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
        for fail_kind in ("failure", "error"):
            fail_node = tc.find(fail_kind)
            if fail_node is None:
                continue
            message = fail_node.get("message", "(no message)")[:400]
            findings.append({
                "title": f"Test failure: {tc.get('name', '?')}",
                "description": (fail_node.text or message).strip()[:2000],
                "severity": "High",
                "file_path": name,
                "line": 0,
                "active": True,
                "verified": False,
                "static_finding": False,
                "dynamic_finding": True,
                "tag": "junit",
            })
            break
    findings.append({
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
    })
    return findings


class Junitparser:
    def get_scan_types(self):
        return ["JUnit Test Report"]

    def get_label_for_scan_types(self, scan_type):
        return scan_type

    def get_description_for_scan_types(self, scan_type):
        return "JUnit XML test results from gradle testDebugUnitTest (or any JUnit3/4-style test framework)."

    def requires_file(self, scan_type):
        return True

    def get_findings(self, file, test):
        if file.name.lower().endswith(".zip"):
            findings = []
            with zipfile.ZipFile(file, "r") as zf:
                for name in zf.namelist():
                    if not name.endswith(".xml"):
                        continue
                    with zf.open(name) as member:
                        findings.extend(_parse_junit(member, name))
            return findings
        with file.open() as f:
            return _parse_junit(f, file.name)
