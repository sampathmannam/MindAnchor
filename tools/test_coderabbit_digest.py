"""Tests for tools/coderabbit-digest.py.

Run with:
    python -m unittest tools.test_coderabbit_digest
"""
import importlib.util
import os
import sys
import unittest
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location(
    "coderabbit_digest", os.path.join(HERE, "coderabbit-digest.py")
)
cr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cr)


# -- Sample bodies used across the test cases -------------------

FORMAT_A_BODY = """\
**Actionable comments posted: 2**

> [!CAUTION]
> Some comments are outside the diff and can't be posted inline due to platform limitations.

<details>
<summary>⚠️ Outside diff range comments (1)</summary><blockquote>

<details>
<summary>app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt (1)</summary><blockquote>

`71-79`: _🩺 Stability & Availability_ | _🟠 Major_ | _⚡ Quick win_

**Gate mindfulness by HealthConnect feature support.**

`isAvailable` only checks that Health Connect is installed. Check `HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION`.

<!-- cr-comment:v1:abc123 -->

</blockquote></details>

</blockquote></details>

<details>
<summary>🧹 Nitpick comments (1)</summary><blockquote>

<details>
<summary>app/src/main/java/org/mindanchor/WellnessSignals.kt (1)</summary><blockquote>

`125-132`: _📐 Maintainability_ | _🔵 Trivial_

**The KDoc promises a check that isReportable does not do.**

The doc says isReportable is false when the median equals the MAD. But the implementation only checks non-null and sample count.

<!-- cr-comment:v1:def456 -->

</blockquote></details>

</blockquote></details>
"""


FORMAT_B_BODY = """\
**Actionable comments posted: 2**

<details>
<summary>🤖 Prompt for all review comments with AI agents</summary>

```
Verify each finding against current code.

Inline comments:
In @.github/workflows/ci.yml:
- Around line 34-38: Update the "Set up Android SDK" step to use the supported packages input, specifying platforms;android-36 and build-tools;36.0.0. Remove
the ignored api-level and build-tools-version inputs so the required Android 36
SDK components are installed.

In @app/src/main/java/org/mindanchor/HealthConnectSource.kt:
- Around line 96-108: Update refreshHealthConnectStatus() to derive both the granted-permission count and total count from
HealthConnectSource.effectivePermissions(app).
```

</details>
"""


# -- Tests ---------------------------------------------------

class TestActionCount(unittest.TestCase):
    def test_extracts_n_from_headline(self):
        self.assertEqual(cr.action_count(FORMAT_A_BODY), 2)

    def test_extracts_n_from_format_b_headline(self):
        self.assertEqual(cr.action_count(FORMAT_B_BODY), 2)

    def test_returns_none_when_no_header(self):
        self.assertIsNone(cr.action_count("no headline here"))


class TestParseSubmittedAt(unittest.TestCase):
    def test_z_suffix(self):
        ts = cr.parse_submitted_at({"submitted_at": "2026-08-09T14:37:53Z"})
        self.assertIsNotNone(ts)
        # Verify by re-formatting through datetime.
        self.assertEqual(
            datetime.fromtimestamp(ts, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "2026-08-09T14:37:53Z",
        )

    def test_offset_suffix(self):
        ts = cr.parse_submitted_at({"submitted_at": "2026-08-09T14:37:53+00:00"})
        self.assertIsNotNone(ts)

    def test_missing_field(self):
        self.assertIsNone(cr.parse_submitted_at({}))

    def test_garbage_value(self):
        self.assertIsNone(cr.parse_submitted_at({"submitted_at": "not-a-date"}))


class TestParseFormatA(unittest.TestCase):
    def setUp(self):
        self.findings = cr.parse_format_a(FORMAT_A_BODY)

    def test_extracts_both_findings(self):
        self.assertEqual(len(self.findings), 2)

    def test_first_finding_is_major_outside_diff(self):
        f = self.findings[0]
        self.assertEqual(f["file"], "app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt")
        self.assertEqual(f["lines"], "71-79")
        self.assertEqual(f["severity"], "Major")
        self.assertEqual(f["section"], "Outside diff")
        self.assertIn("Gate mindfulness", f["title"])

    def test_second_finding_is_trivial_nitpick(self):
        f = self.findings[1]
        self.assertEqual(f["file"], "app/src/main/java/org/mindanchor/WellnessSignals.kt")
        self.assertEqual(f["lines"], "125-132")
        self.assertEqual(f["severity"], "Trivial")
        self.assertEqual(f["section"], "Nitpick")
        self.assertIn("KDoc promises", f["title"])

    def test_quick_win_flag_parsed(self):
        # First finding has a `Quick win` segment; second does not.
        self.assertTrue(self.findings[0]["quick_win"])
        self.assertFalse(self.findings[1]["quick_win"])


class TestParseFormatB(unittest.TestCase):
    def setUp(self):
        # Force format-A to return empty so we test format-B.
        self.findings = cr.parse_format_b(FORMAT_B_BODY)

    def test_extracts_inline_findings(self):
        self.assertEqual(len(self.findings), 2)

    def test_first_finding(self):
        f = self.findings[0]
        self.assertEqual(f["file"], ".github/workflows/ci.yml")
        self.assertEqual(f["lines"], "34-38")
        # Format B has no severity emoji — parser defaults to "Unknown".
        self.assertEqual(f["severity"], "Unknown")
        self.assertEqual(f["section"], "Inline")

    def test_multi_line_description_collapsed(self):
        f = self.findings[0]
        # The CI finding has 3 wrapped lines in the body.
        # Title should be a single sentence, not just the first wrapped line.
        self.assertNotIn("\n", f["title"])
        # Body should preserve the full description including
        # the second line that was a continuation.
        self.assertIn("Remove", f["body"])
        self.assertIn("api-level", f["body"])

    def test_leading_at_stripped_from_path(self):
        # The CI finding is `In @.github/...` — the `@` should
        # not leak into the captured file path.
        f = self.findings[0]
        self.assertFalse(f["file"].startswith("@"))


class TestParseReviewDispatch(unittest.TestCase):
    def test_format_a_dispatch(self):
        findings = cr.parse_review(FORMAT_A_BODY)
        self.assertEqual(len(findings), 2)
        self.assertEqual(findings[0]["severity"], "Major")

    def test_format_b_dispatch(self):
        findings = cr.parse_review(FORMAT_B_BODY)
        self.assertEqual(len(findings), 2)
        self.assertEqual(findings[0]["severity"], "Unknown")


class TestSeverityOrdering(unittest.TestCase):
    def test_major_sorts_before_trivial(self):
        # Major=0, Trivial=2 — Major < Trivial.
        self.assertLess(cr.SEV_RANK["Major"], cr.SEV_RANK["Trivial"])
        self.assertLess(cr.SEV_RANK["Trivial"], cr.SEV_RANK["Nitpick"])
        self.assertLess(cr.SEV_RANK["Nitpick"], cr.SEV_RANK["Unknown"])

    def test_sorting_finds_major_first(self):
        # Insert findings in shuffled order; sort by
        # (severity_rank, file, lines) and check Major comes first.
        findings = [
            {"file": "b.kt", "lines": "1", "severity": "Trivial", "title": "t"},
            {"file": "a.kt", "lines": "1", "severity": "Major", "title": "m"},
        ]
        sorted_ = sorted(
            findings,
            key=lambda x: (cr.SEV_RANK.get(x["severity"], 99), x["file"], x["lines"]),
        )
        self.assertEqual(sorted_[0]["severity"], "Major")


if __name__ == "__main__":
    unittest.main()
