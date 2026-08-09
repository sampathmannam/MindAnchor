"""Tests for tools/ci-log-digest.py.

Run with:
    python -m unittest tools.test_ci_log_digest
"""
import importlib.util
import os
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location(
    "ci_log_digest", os.path.join(HERE, "ci-log-digest.py")
)
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


# -- Sample log lines -----------------------------------------

LOG_LINE = (
    "build\tBuild, lint and test\t"
    "2026-08-09T14:55:35.4129300Z "
    "##[error]/home/runner/work/repo/repo/app/F.kt:42: Error: To call X() "
    "[ForegroundServiceType]"
)

LOG_LINE_WARN = (
    "build\tBuild, lint and test\t"
    "2026-08-09T14:55:35.4129300Z "
    "##[warning]some warning here"
)

LOG_LINE_CMD = (
    "build\tBuild, lint and test\t"
    "2026-08-09T14:55:35.4129300Z "
    "::error::config validation failed"
)

LOG_LINE_PLAIN = (
    "build\tBuild, lint and test\t"
    "2026-08-09T14:55:35.4129300Z "
    "> Task :app:compileDebugKotlin"
)

LOG_CONTINUATION = "  continuation of the previous line"


# -- Tests ---------------------------------------------------

class TestSplitLogLine(unittest.TestCase):
    def test_full_line_returns_step_and_content(self):
        step, content = ci.split_log_line(LOG_LINE)
        self.assertEqual(step, "build")
        self.assertTrue(content.startswith("##[error]"))

    def test_error_parsed(self):
        by_step = ci.parse_log(LOG_LINE)
        self.assertEqual(len(by_step), 1)
        errors = by_step["Build, lint and test"]["errors"]
        self.assertEqual(len(errors), 1)
        # The error body is the content with the ##[error] prefix stripped.
        self.assertIn("To call X()", errors[0])
        self.assertIn("ForegroundServiceType", errors[0])

    def test_warning_parsed(self):
        by_step = ci.parse_log(LOG_LINE_WARN)
        self.assertEqual(len(by_step["Build, lint and test"]["warnings"]), 1)
        self.assertEqual(len(by_step["Build, lint and test"]["errors"]), 0)

    def test_cmd_error_parsed(self):
        by_step = ci.parse_log(LOG_LINE_CMD)
        errors = by_step["Build, lint and test"]["errors"]
        self.assertEqual(len(errors), 1)
        self.assertIn("config validation failed", errors[0])

    def test_plain_line_no_errors(self):
        by_step = ci.parse_log(LOG_LINE_PLAIN)
        self.assertEqual(by_step["Build, lint and test"]["errors"], [])
        self.assertEqual(by_step["Build, lint and test"]["warnings"], [])

    def test_continuation_line_ignored(self):
        # A line without a timestamp and without tabs is a
        # continuation; it carries no annotation on its own.
        by_step = ci.parse_log(LOG_LINE_PLAIN + "\n" + LOG_CONTINUATION)
        self.assertEqual(len(by_step["Build, lint and test"]["errors"]), 0)

    def test_multiple_lines_grouped_by_display_step(self):
        log = "\n".join([
            LOG_LINE,
            "build\tBuild, lint and test\t2026-08-09T14:55:36.0000000Z "
            "##[error]second error in same step",
            "detekt\tRun detekt\t2026-08-09T14:55:37.0000000Z "
            "##[error]error in detekt step",
        ])
        by_step = ci.parse_log(log)
        self.assertEqual(len(by_step["Build, lint and test"]["errors"]), 2)
        self.assertEqual(len(by_step["Run detekt"]["errors"]), 1)


class TestExtractFileRefs(unittest.TestCase):
    def test_extracts_repo_relative_path(self):
        # The /home/runner/work/<owner>/<repo>/ prefix is stripped.
        text = "/home/runner/work/repo/repo/app/src/main/F.kt:42:1: Error"
        refs = ci.extract_first_file_refs(text, max_refs=3)
        self.assertEqual(len(refs), 1)
        self.assertEqual(refs[0], "`app/src/main/F.kt:42`")

    def test_filters_to_known_extensions(self):
        # "Service.startForeground()" should NOT match because
        # "startForeground" is not a known file extension.
        text = "To call Service.startForeground(), the manifest"
        self.assertEqual(ci.extract_first_file_refs(text, max_refs=3), [])

    def test_max_refs_caps_output(self):
        text = " ".join([f"a.kt:{i}" for i in range(10)])
        refs = ci.extract_first_file_refs(text, max_refs=3)
        self.assertEqual(len(refs), 3)

    def test_no_line_number_ignored(self):
        # A bare path without :LINE is not a file reference.
        text = "see app/src/main/F.kt for context"
        self.assertEqual(ci.extract_first_file_refs(text, max_refs=3), [])

    def test_multiple_extensions_supported(self):
        text = "Foo.kt:5 and Bar.java:7 and Baz.kts:9 and Q.gradle:11"
        # Each is a different ext; cap at 5 to keep the test focused.
        refs = ci.extract_first_file_refs(text, max_refs=5)
        exts = [r.split(":")[0].lstrip("`") for r in refs]
        # Confirm kt and java are present at minimum.
        self.assertTrue(any(e.endswith(".kt") for e in exts))
        self.assertTrue(any(e.endswith(".java") for e in exts))


class TestFirstErrorSummary(unittest.TestCase):
    def test_strips_file_ref_from_prose(self):
        err = "/home/runner/work/r/r/app/src/main/F.kt:42: Error: To call X() [Rule]"
        prose, refs = ci.first_error_summary(err)
        # Prose should not include the file ref or the line prefix.
        self.assertNotIn("app/src/main/F.kt", prose)
        self.assertIn("To call X()", prose)
        # Refs should include the file.
        self.assertEqual(len(refs), 1)
        self.assertEqual(refs[0], "`app/src/main/F.kt:42`")

    def test_long_prose_truncated(self):
        err = "x" * 500
        prose, _ = ci.first_error_summary(err)
        self.assertLessEqual(len(prose), 220)


class TestNormalizeJobs(unittest.TestCase):
    def test_jobs_list_passed_through(self):
        # fmt_digest expects run["jobs"] to be a list (the
        # fetch_run normaliser wraps a single-object jobs
        # into a list). Verify a failed step renders.
        run = {
            "databaseId": 1, "name": "CI", "displayTitle": "x",
            "conclusion": "failure", "headBranch": "main", "headSha": "deadbeef",
            "event": "push", "createdAt": "2026-08-09T00:00:00Z",
            "updatedAt": "2026-08-09T00:00:00Z", "url": "x",
            "jobs": [{
                "name": "build", "conclusion": "failure",
                "steps": [
                    {"name": "Run tests", "number": 1, "conclusion": "failure"},
                ],
            }],
        }
        text = ci.fmt_digest(run, by_step={"Run tests": {"errors": ["boom"], "warnings": []}})
        self.assertIn("Job: `build`", text)
        self.assertIn("Step 1: `Run tests`", text)

    def test_jobs_empty_list_renders_no_jobs(self):
        run = {
            "databaseId": 1, "name": "CI", "displayTitle": "x",
            "conclusion": "failure", "headBranch": "main", "headSha": "deadbeef",
            "event": "push", "createdAt": "2026-08-09T00:00:00Z",
            "updatedAt": "2026-08-09T00:00:00Z", "url": "x",
            "jobs": [],
        }
        # Empty jobs → no job section, no "did not surface"
        # line either (that's the "had jobs but no error
        # annotations" message).
        text = ci.fmt_digest(run, by_step={})
        self.assertIn("(no job data)", text)


class TestFmtDigest(unittest.TestCase):
    def test_no_errors_shows_specific_message(self):
        run = {
            "databaseId": 42, "name": "CI", "displayTitle": "x",
            "conclusion": "failure", "headBranch": "main", "headSha": "deadbeef",
            "event": "push", "createdAt": "2026-08-09T00:00:00Z",
            "updatedAt": "2026-08-09T00:00:00Z", "url": "x",
            "jobs": [{"name": "build", "conclusion": "failure", "steps": []}],
        }
        text = ci.fmt_digest(run, by_step={})
        self.assertIn("did not surface explicit error annotations", text)

    def test_url_rendered(self):
        run = {
            "databaseId": 42, "name": "CI", "displayTitle": "x",
            "conclusion": "failure", "headBranch": "main", "headSha": "deadbeef",
            "event": "push", "createdAt": "2026-08-09T00:00:00Z",
            "updatedAt": "2026-08-09T00:00:00Z",
            "url": "https://github.com/foo/bar/actions/runs/42",
            "jobs": [],
        }
        text = ci.fmt_digest(run, by_step={})
        self.assertIn("https://github.com/foo/bar/actions/runs/42", text)


if __name__ == "__main__":
    unittest.main()
