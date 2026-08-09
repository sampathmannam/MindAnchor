#!/usr/bin/env python3
"""Summarise a failed GitHub Actions run.

`gh run view <id> --log-failed` returns the full log of
every failed step, typically hundreds of lines. The
headline information is in a much smaller subset:

  - Failed step names (from --json jobs)
  - `##[error]...` and `::error::...` annotations
  - File:line references
  - Gradle/Java stack frames

This script pulls the JSON summary plus the failed-log
stream, parses the error lines, groups them by step,
and prints a compact Markdown digest.

Usage:
  python tools/ci-log-digest.py --run 31319362341
  python tools/ci-log-digest.py --run 31319362341 --repo owner/name
"""
import argparse
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict


def gh_json(args):
    """Run `gh <args>` and return parsed JSON."""
    cmd = ["gh"] + args
    if "--paginate" in cmd and "--slurp" not in cmd:
        cmd.append("--slurp")
    result = subprocess.run(
        cmd, capture_output=True, text=True, encoding="utf-8", check=True
    )
    parsed = json.loads(result.stdout)
    if isinstance(parsed, list) and all(isinstance(p, list) for p in parsed):
        return [item for page in parsed for item in page]
    return parsed


def gh_text(args):
    """Run `gh <args>` and return stdout text."""
    result = subprocess.run(
        ["gh"] + args, capture_output=True, text=True, encoding="utf-8", check=True
    )
    return result.stdout


# -- Log-line patterns -----------------------------------------
# Each line in `gh run view --log-failed` is tab-separated
# with THREE fields:
#   <step_name>\t<display-step-name>\t<TIMESTAMP> <log-content>
# The first two are usually the same identifier (the
# short alias and the full display name); either one
# is fine for grouping annotations. The third field
# carries both the ISO 8601 timestamp and the log line
# itself, separated by a single space. Some lines are
# continuations of the previous log line and have no
# leading tabs — those start without a tab.
LOG_LINE_RE = re.compile(
    r"^(?P<step>[^\t]*)\t(?P<display>[^\t]*)\t(?P<rest>.*)$"
)
TS_RE = re.compile(r"^(\d{4}-\d{2}-\d{2}T\S+)\s(.*)$", re.DOTALL)

# -- Error / warning annotations -------------------------------
ERROR_RE = re.compile(r"^##\[error\](.*)$")
ERROR_CMD_RE = re.compile(r"^::error::(.*)$")
WARNING_RE = re.compile(r"^##\[warning\](.*)$")

# -- File references --------------------------------------------
# Matches: path/to/file.kt:42  or  path/to/file.kt:42:1
# The line number is REQUIRED so we don't pick up
# identifier-like strings ("Service.startForeground()")
# as file references.
FILE_REF_RE = re.compile(
    r"((?:[A-Za-z0-9_./-]+/)?[A-Za-z0-9_.-]+\.[A-Za-z0-9]+):(\d+)(?::(\d+))?"
)


def split_log_line(line):
    """Return (step_name, log_content) for a `gh run view`
    log line, or (None, raw_line) for a non-tab-prefixed
    continuation line.

    The log stream has two shapes:
      - Full step-prefixed lines: `<step>\t<display>\t<TIMESTAMP> <content>`
      - Continuation lines: no leading tabs, no timestamp.
    """
    if "\t" in line:
        m = LOG_LINE_RE.match(line)
        if m:
            ts_m = TS_RE.match(m.group("rest"))
            if ts_m:
                return m.group("step"), ts_m.group(2)
    return None, line


def parse_log(log_text):
    """Return a per-step list of error/warning entries.

    Keys are the *display* step name (the second tab
    field of the log line), since that's what the
    `gh run view --json jobs` API uses. Falls back to
    the short alias if the display field is empty.
    """
    by_step = defaultdict(lambda: {"errors": [], "warnings": []})
    for line in log_text.splitlines():
        if "\t" not in line:
            continue
        m = LOG_LINE_RE.match(line)
        if not m:
            continue
        display = m.group("display") or m.group("step")
        ts_m = TS_RE.match(m.group("rest"))
        if not ts_m:
            continue
        content = ts_m.group(2)
        em = ERROR_RE.match(content)
        if em:
            by_step[display]["errors"].append(em.group(1).strip())
            continue
        em = ERROR_CMD_RE.match(content)
        if em:
            by_step[display]["errors"].append(em.group(1).strip())
            continue
        wm = WARNING_RE.match(content)
        if wm:
            by_step[display]["warnings"].append(wm.group(1).strip())
    return by_step


def extract_first_file_refs(text, max_refs=3):
    """Pull up to `max_refs` file:line references from a
    log line. Strips the GitHub Actions workspace
    prefix (`/home/runner/work/<owner>/<repo>/`) so the
    user sees the in-repo path that matches their local
    checkout. The file extension is filtered to plausible
    values so we don't pick up Gradle progress
    percentages like `10%` or `:42` from version
    strings.
    """
    refs = []
    for m in FILE_REF_RE.finditer(text):
        path, line, _ = m.group(1), m.group(2), m.group(3)
        ext = path.rsplit(".", 1)[-1].lower()
        if ext not in {
            "kt", "java", "xml", "kts", "gradle", "py",
            "yml", "yaml", "sh", "toml", "json", "md",
            "txt", "properties", "cfg",
        }:
            continue
        path = re.sub(r"^/home/runner/work/[^/]+/[^/]+/", "", path)
        refs.append(f"`{path}:{line}`")
        if len(refs) >= max_refs:
            break
    return refs


def first_error_summary(error_text):
    """Reduce a single error annotation to one scannable line.

    Strips leading `path/file.kt:LINE: ` if present, trims
    the message, and appends any file references found
    further down the line.
    """
    text = error_text.strip()
    # Strip an optional leading "##[error]" style prefix that
    # some annotations repeat; we already removed it.
    refs = extract_first_file_refs(text, max_refs=2)
    # Drop the file references from the prose copy; we'll
    # show them separately.
    prose = FILE_REF_RE.sub("", text).strip(" :,-")
    # Collapse internal whitespace.
    prose = " ".join(prose.split())
    if len(prose) > 220:
        prose = prose[:217] + "..."
    return prose, refs


def fetch_run(run_id, repo):
    """Return the run summary JSON. Normalise `jobs` to a
    list — the GitHub API returns a single object when
    the run has exactly one job and a list otherwise.
    """
    run = gh_json([
        "run", "view", run_id,
        "--repo", repo,
        "--json", "databaseId,name,displayTitle,conclusion,headBranch,headSha,event,createdAt,updatedAt,url,jobs",
    ])
    jobs = run.get("jobs")
    if jobs is None:
        run["jobs"] = []
    elif isinstance(jobs, dict):
        run["jobs"] = [jobs]
    return run


def fetch_log(run_id, repo):
    """Return the failed-step log as text."""
    return gh_text([
        "run", "view", run_id, "--repo", repo, "--log-failed",
    ])


def fmt_digest(run, by_step):
    """Format a Markdown digest for a run."""
    name = run.get("name", "?")
    title = run.get("displayTitle", "?")
    conclusion = run.get("conclusion", "?")
    branch = run.get("headBranch", "?")
    url = run.get("url", "")

    lines = []
    lines.append(f"### CI run `{run.get('databaseId')}` — {name} — **{conclusion.upper()}**")
    lines.append("")
    lines.append(f"Branch: `{branch}`  |  Head: `{run.get('headSha', '?')[:8]}`")
    if url:
        lines.append(f"[Open on GitHub]({url})")
    lines.append(f"PR title: {title}")
    lines.append("")

    if not run.get("jobs"):
        lines.append("(no job data)")
        return "\n".join(lines)

    total_errors = 0
    total_warnings = 0
    for job in run["jobs"]:
        job_name = job.get("name", "?")
        job_conclusion = job.get("conclusion", "?")
        failed_steps = [s for s in (job.get("steps") or []) if s.get("conclusion") == "failure"]
        if not failed_steps:
            continue
        lines.append(f"#### Job: `{job_name}` — {job_conclusion}")
        for step in failed_steps:
            step_name = step.get("name", "?")
            step_num = step.get("number", "?")
            entries = by_step.get(step_name, {"errors": [], "warnings": []})
            errors = entries["errors"]
            warnings = entries["warnings"]
            total_errors += len(errors)
            total_warnings += len(warnings)
            lines.append("")
            lines.append(f"**Step {step_num}: `{step_name}`** — {len(errors)} error(s), {len(warnings)} warning(s)")
            for err in errors[:3]:
                prose, refs = first_error_summary(err)
                if refs:
                    lines.append(f"  - {prose}")
                    for r in refs:
                        lines.append(f"    - at {r}")
                else:
                    lines.append(f"  - {prose}")
            if len(errors) > 3:
                lines.append(f"  - ... and {len(errors) - 3} more error(s)")
        lines.append("")

    if total_errors == 0 and total_warnings == 0:
        lines.append("No `##[error]` / `::error::` annotations parsed from the log.")
        lines.append("Check the raw log: `gh run view <run-id> --log-failed`")
        lines.append("")

    # TL;DR
    failed_step_count = sum(
        1
        for job in (run.get("jobs") or [])
        for step in (job.get("steps") or [])
        if step.get("conclusion") == "failure"
    )
    if total_errors:
        lines.append(f"**TL;DR:** {total_errors} error annotation(s) across {failed_step_count} failed step(s).")
    else:
        lines.append("**TL;DR:** run failed but the log did not surface explicit error annotations. Inspect the raw log for context.")
    lines.append("")
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    p.add_argument("--run", required=True, help="Run ID (databaseId)")
    p.add_argument("--repo", default="sampathmannam/MindAnchor")
    p.add_argument("--json", action="store_true", help="Emit parsed digest as JSON")
    args = p.parse_args()

    run = fetch_run(args.run, args.repo)
    if args.json:
        log_text = fetch_log(args.run, args.repo)
        by_step = parse_log(log_text)
        out = {
            "run": {
                "id": run.get("databaseId"),
                "name": run.get("name"),
                "conclusion": run.get("conclusion"),
                "branch": run.get("headBranch"),
                "sha": run.get("headSha"),
                "url": run.get("url"),
            },
            "failed_steps": [
                {
                    "job": job.get("name"),
                    "step": s.get("name"),
                    "number": s.get("number"),
                    "errors": by_step.get(s.get("name"), {}).get("errors", []),
                    "warnings": by_step.get(s.get("name"), {}).get("warnings", []),
                }
                for job in (run.get("jobs") or [])
                for s in (job.get("steps") or [])
                if s.get("conclusion") == "failure"
            ],
        }
        print(json.dumps(out, indent=2, ensure_ascii=False))
        return

    log_text = fetch_log(args.run, args.repo)
    by_step = parse_log(log_text)
    print(fmt_digest(run, by_step))


if __name__ == "__main__":
    main()
