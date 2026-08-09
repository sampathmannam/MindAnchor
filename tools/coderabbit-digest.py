#!/usr/bin/env python3
"""Summarise a CodeRabbit PR review.

CodeRabbit review bodies are ~25 KB of structured markdown
on a small PR and balloon from there. The actionable
content is in:

  - `**Actionable comments posted: N**`  (headline count)
  - `<details><summary>... </summary>` sections that group
    findings by file
  - Per-finding: backtick-quoted `lines:`, then a
    `_Category_ | _Severity_ | _Quick-win?_` triplet, then
    a bolded title, then prose
  - Comment ids: `<!-- cr-comment:v1:HASH -->`

The rest (autofix prompt, walkthrough, pre-merge checks,
review info, rate-limit warnings, rate-limit-mode
walkthroughs) is boilerplate.

This script pulls the most recent (or a specified) review
from a PR via `gh api`, parses out the actionable findings,
and prints a compact digest. Output is plain Markdown
suitable for an in-chat summary or a release-note.

Usage:
  python tools/coderabbit-digest.py --pr 28
  python tools/coderabbit-digest.py --pr 28 --run b53d744a-c364-4f49-86d6-a2ac0f509aee
  python tools/coderabbit-digest.py --pr 28 --all
"""
import argparse
import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

# -- Severity map (CodeRabbit colour emoji -> label / rank) ----
SEV_EMOJI = {
    "\U0001f7e0": "Major",     # 🟠
    "\U0001f7e1": "Minor",     # 🟡
    "\U0001f535": "Trivial",   # 🔵
    "\U0001f7e3": "Nitpick",   # 🟣
}
SEV_RANK = {"Major": 0, "Minor": 1, "Trivial": 2, "Nitpick": 3, "Unknown": 4}

# -- Section labels in CodeRabbit's <summary> ---------------
SECTION_LABELS = {
    "Outside diff range": "Outside diff",
    "Nitpick comments": "Nitpick",
    "Inline comments": "Inline",
    "Outside diff comments": "Outside diff",
}

# -- Pattern: `lines`: _Category_ | _Severity_ | _Optional_ --
FINDING_RE = re.compile(
    r"`(?P<lines>\d+(?:-\d+)?)`:\s*"
    r"_(?P<category>[^_]+)_\s*\|\s*"
    r"_(?P<severity>[^_]+)_\s*"
    r"(?:\|\s*_(?P<quick>[^_]+)_\s*)?"
    r"\n\n\*\*"
    r"(?P<title>[^*\n]+)\*\*"
    r"(?P<body>.*?)"
    r"(?=<!--\s*cr-comment:|\Z)",
    re.DOTALL,
)
TITLE_RE = re.compile(r"^\*\*([^*\n]+)\*\*")
FILE_HEADER_RE = re.compile(r"<summary>(?P<path>[\w./-]+\.[a-zA-Z]+)\s*\((?P<count>\d+)\)</summary>")
SECTION_RE = re.compile(r"<summary>(?P<label>[^<]*)</summary>")


def gh_json(args):
    """Run `gh <args> --json ... -r .` and return parsed JSON.

    Uses `--paginate --slurp` so the response is a single
    JSON array regardless of how many pages the endpoint
    returns. `--paginate` alone concatenates the per-page
    JSON documents, which the default JSON parser handles,
    but `--slurp` makes the contract explicit and survives
    endpoints that don't set a `Link: rel="next"` header.

    `gh api --paginate --slurp` returns a list of one
    JSON array per page (so the outer value is a list of
    lists). When the endpoint returns a single page, the
    outer list has one entry; we flatten to a single
    list of items.

    Raises on non-zero exit so the cron agent sees the
    error rather than parsing empty output silently.
    """
    cmd = ["gh"] + args
    if "--paginate" in cmd and "--slurp" not in cmd:
        cmd.append("--slurp")
    result = subprocess.run(
        cmd, capture_output=True, text=True, encoding="utf-8", check=True
    )
    parsed = json.loads(result.stdout)
    if (
        isinstance(parsed, list)
        and all(isinstance(p, list) for p in parsed)
    ):
        # Flatten the per-page arrays.
        return [item for page in parsed for item in page]
    return parsed


def fetch_reviews(repo, pr):
    """Return the list of CodeRabbit reviews on a PR."""
    data = gh_json([
        "api",
        f"repos/{repo}/pulls/{pr}/reviews",
        "--paginate",
    ])
    return [r for r in data if r.get("user", {}).get("login") == "coderabbitai[bot]"]


def fetch_issue_comments(repo, pr):
    """Return the list of issue-comments authored by CodeRabbit.

    CodeRabbit also posts a walkthrough comment on the PR
    issue thread; that's where the walkthrough / autofix
    sections live when the bot is in walkthrough mode.
    """
    data = gh_json([
        "api",
        f"repos/{repo}/issues/{pr}/comments",
        "--paginate",
    ])
    return [c for c in data if c.get("user", {}).get("login") == "coderabbitai[bot]"]


def _normalize_issue_comment(c):
    """Shape an issue-comment into the same dict shape as a review.

    Both `pulls/{pr}/reviews` and `issues/{pr}/comments` carry a
    body and a login, but their field names differ: reviews
    use `submitted_at`, comments use `created_at`. The walker
    in `load_reviews_for` doesn't care which; it just needs
    one timeline key.
    """
    return {
        "id": c.get("id"),
        "user": c.get("user", {}),
        "body": c.get("body", ""),
        "submitted_at": c.get("created_at"),
        "state": "COMMENTED",  # issue comments have no review state
        "via_issue_comment": True,
    }


def load_reviews_for(repo, pr):
    """Return a unified, time-ordered list of CodeRabbit review
    payloads on a PR.

    Merges `pulls/{pr}/reviews` (the formal review API) and
    `issues/{pr}/comments` (the walkthrough / autofix comments
    that CodeRabbit posts as issue comments rather than as
    reviews). The merged list is sorted by `submitted_at` so
    the most recent entry is the last one.

    Issue comments are filtered to those whose body
    actually carries the CodeRabbit review header
    (`**Actionable comments posted:**`). Otherwise the
    loader picks up unrelated issue-comment chatter —
    rate-limit "no" replies, "I'll re-review" acks,
    walkthrough-only summaries, etc. — and surfaces them
    as empty reviews.
    """
    reviews = fetch_reviews(repo, pr)
    raw_comments = fetch_issue_comments(repo, pr)
    review_comments = [
        _normalize_issue_comment(c)
        for c in raw_comments
        if "**Actionable comments posted:" in (c.get("body") or "")
    ]
    unified = list(reviews) + review_comments
    unified.sort(key=lambda r: r.get("submitted_at") or "")
    return unified


def parse_review(body):
    """Return a list of finding dicts from a review body.

    Two formats exist in the wild:

    Format A — structured (full reviews with inline comments):
        <details><summary>Section (N)</summary>
        <details><summary>path/to/file.kt (M)</summary>
        `LINE-LINE`: _Category_ | _Severity_ | _Quick-win?_
        **Title in bold.** body...
        <!-- cr-comment:v1:HASH -->
        </details></details>

    Format B — textual list (rate-limited walkthroughs, or
    when the bot posts only the prompt for AI agents):
        **Actionable comments posted: N**
        <details><summary>🤖 Prompt for all review
        comments with AI agents</summary>
        ```
        Inline comments:
        In `@path/to/file.kt`:
        - Around line X-Y: description...
        - Line X: description...
        ```

    Each finding: {file, lines, severity, category, title,
    body, section}.
    """
    findings = parse_format_a(body)
    if findings:
        return findings
    return parse_format_b(body)


def parse_format_a(body):
    findings = []
    file_header_iter = list(FILE_HEADER_RE.finditer(body))
    section_header_iter = list(SECTION_RE.finditer(body))
    for m in FINDING_RE.finditer(body):
        lines = m.group("lines")
        category = m.group("category").strip()
        sev_emoji = m.group("severity").strip()
        quick = (m.group("quick") or "").strip()
        title = m.group("title").strip()
        finding_body = m.group("body").strip()
        severity = sev_emoji
        for emo, label in SEV_EMOJI.items():
            if sev_emoji.startswith(emo):
                severity = label
                break
        file_path = "<unknown>"
        for fh in file_header_iter:
            if fh.start() < m.start():
                file_path = fh.group("path")
            else:
                break
        section = ""
        for sh in section_header_iter:
            if sh.start() < m.start():
                label = sh.group("label")
                for k, v in SECTION_LABELS.items():
                    if k in label:
                        section = v
                        break
            else:
                break
        findings.append({
            "file": file_path,
            "lines": lines,
            "severity": severity,
            "category": category,
            "quick_win": bool(quick),
            "title": title,
            "body": finding_body,
            "section": section,
        })
    return findings


# Format B: the textual list inside the
# "🤖 Prompt for all review comments" block. The block
# sits between ``` fences. Sections are introduced by
# `Inline comments:` / `Outside diff comments:` /
# `Nitpick comments:` headers; file headers by
# `In \`@path\``:` and items by `- Around line X-Y:`.
PROMPT_BLOCK_RE = re.compile(r"```\n(?P<body>.*?)```", re.DOTALL)
SECTION_HEADER_B_RE = re.compile(
    r"^(?P<kind>Inline comments|Outside diff(?: range)? comments|Nitpick comments):\s*$",
    re.MULTILINE,
)
FILE_HEADER_B_RE = re.compile(
    r"^In\s+@?`?(?P<path>[^`:\s]+)`?\s*:\s*$",
    re.MULTILINE,
)
ITEM_B_RE = re.compile(
    r"^-\s*(?P<loc>Around line|Line)\s*(?P<lines>\d+(?:-\d+)?)\s*:\s*(?P<desc>.*)$",
    re.MULTILINE,
)


def strip_at(path):
    """CodeRabbit writes `In @path:` in some reviews and
    `In \`@path\`:` in others; both leak a leading `@` into
    the captured path. Strip it.
    """
    return path.lstrip("@")


def parse_format_b(body):
    """Parse the 'Prompt for all review comments' textual block.

    Items are multi-line: the first line is `- Around
    line X-Y: <text>` and continuation lines follow flush-
    left. The next item starts with `- Around line` or
    `- Line`, the next file with `In \`@path\`:` or
    `In @path:`, the next section with `Nitpick
    comments:` / `Inline comments:` / `Outside diff
    comments:`. A blank line ends an item.
    """
    findings = []
    pb = PROMPT_BLOCK_RE.search(body)
    if not pb:
        return findings
    block = pb.group("body")
    section_kind = "Inline"
    file_path = "<unknown>"
    current = None
    for line in block.splitlines():
        m_sec = SECTION_HEADER_B_RE.match(line)
        if m_sec:
            if current is not None:
                findings.append(_b_item_to_finding(current, file_path, section_kind))
                current = None
            kind = m_sec.group("kind")
            if "Nitpick" in kind:
                section_kind = "Nitpick"
            elif "Outside" in kind:
                section_kind = "Outside diff"
            else:
                section_kind = "Inline"
            continue
        m_file = FILE_HEADER_B_RE.match(line)
        if m_file:
            if current is not None:
                findings.append(_b_item_to_finding(current, file_path, section_kind))
                current = None
            file_path = strip_at(m_file.group("path"))
            continue
        m_item = ITEM_B_RE.match(line)
        if m_item:
            if current is not None:
                findings.append(_b_item_to_finding(current, file_path, section_kind))
            current = {
                "lines": m_item.group("lines"),
                "desc_lines": [m_item.group("desc").strip()],
            }
            continue
        # Blank line ends the current item.
        if not line.strip():
            if current is not None:
                findings.append(_b_item_to_finding(current, file_path, section_kind))
                current = None
            continue
        # Continuation of the current item (any other non-
        # blank line that doesn't start a new construct).
        if current is not None:
            current["desc_lines"].append(line.strip())
    if current is not None:
        findings.append(_b_item_to_finding(current, file_path, section_kind))
    return findings


def _b_item_to_finding(item, file_path, section_kind):
    desc = " ".join(item["desc_lines"]).strip()
    sentence = re.split(r"(?<=[.!?])\s+", desc, maxsplit=1)[0]
    if 8 <= len(sentence) <= 110:
        title = sentence
    else:
        title = desc[:107] + ("..." if len(desc) > 110 else "")
    return {
        "file": file_path,
        "lines": item["lines"],
        # Format B (the rate-limited walkthrough / prompt-only
        # mode) doesn't carry a severity emoji, so the review
        # can't be bucketed into Major / Minor / Trivial /
        # Nitpick. Surface that as "Unknown" so the digest
        # doesn't mislabel rate-limited walkthroughs as low-
        # priority Trivial findings.
        "severity": "Unknown",
        "category": "CodeRabbit",
        "quick_win": False,
        "title": title,
        "body": desc,
        "section": section_kind,
    }


def action_count(body):
    """Pull `**Actionable comments posted: N**` from the body."""
    m = re.search(r"\*\*Actionable comments posted:\s*(\d+)\*\*", body)
    return int(m.group(1)) if m else None


def parse_submitted_at(review):
    """Return the review's submitted_at as a POSIX timestamp, or None.

    The GitHub API returns ISO 8601; Python's fromisoformat
    (3.11+) handles the `Z` suffix and the fractional
    seconds that `Z` is sometimes paired with.
    """
    raw = review.get("submitted_at")
    if not raw:
        return None
    try:
        from datetime import datetime, timezone
        # Tolerate the trailing Z.
        if raw.endswith("Z"):
            raw = raw[:-1] + "+00:00"
        return datetime.fromisoformat(raw).timestamp()
    except (ValueError, TypeError):
        return None


def line_range_start_end(lines):
    """Parse a CodeRabbit line spec like `34-38` or `42` into (start, end)."""
    if "-" in lines:
        a, b = lines.split("-", 1)
        return int(a), int(b)
    n = int(lines)
    return n, n


def line_last_touched(file_path, lines, repo_dir, review_time):
    """Return the commit time of the most recent edit to the
    cited line range, or None if we can't tell.

    Two-step lookup:

    1. `git log -L start,end:file` is the right tool here:
       it tracks the cited line range across the file's
       history and reports every commit that touched it.
       That survives the case where the cited lines
       shifted because of an edit elsewhere in the file
       (a `git blame -L start,end` against the current
       checkout would return SHAs for whatever content
       now lives at those line numbers, which may be
       unrelated to the cited finding).
    2. For each such commit, pick the most recent
       timestamp. The answer is "addressed" if any cited
       line was edited after the review.

    Fallback: when `git log -L` fails (line range past
    EOF, file deleted, etc.), fall back to `git blame
    -L` so the tool still surfaces an answer — it's
    just less precise.
    """
    if not repo_dir or not file_path or not lines:
        return None
    start, end = line_range_start_end(lines)

    # Primary: `git log -L` lists every commit that touched
    # the line range. Restrict to commits at or before
    # `review_time` because we want to know whether the
    # *pre-review* state is still in place; the commits
    # after review_time are themselves evidence the range
    # moved.
    most_recent = _log_L_touched_time(file_path, start, end, repo_dir, review_time)
    if most_recent is not None:
        return most_recent

    # Fallback: blame the cited lines in the current
    # checkout. Less precise (line numbers may have
    # shifted) but better than nothing.
    return _blame_touched_time(file_path, start, end, repo_dir)


def _log_L_touched_time(file_path, start, end, repo_dir, review_time):
    """Return the most recent commit time in the line-range
    history, or None if `git log -L` cannot resolve the
    range.
    """
    try:
        log = subprocess.run(
            ["git", "log", "-L", f"{start},{end}:{file_path}",
             "--since=1970-01-01", "--until=@" + str(int(review_time)),
             "--format=%ct"],
            cwd=repo_dir, capture_output=True, text=True, encoding="utf-8",
            timeout=15,
        )
    except (subprocess.TimeoutExpired, OSError):
        return None
    if log.returncode != 0:
        return None
    timestamps = []
    for line in log.stdout.splitlines():
        line = line.strip()
        if line.isdigit():
            timestamps.append(int(line))
    if not timestamps:
        # `git log -L` returns no commit lines if the
        # range was created post-review (which means
        # there's no pre-review history to track). In
        # that case the cited line range simply did not
        # exist in the reviewed revision. Fall through
        # to blame.
        return None
    return max(timestamps)


def _blame_touched_time(file_path, start, end, repo_dir):
    """Fallback: blame the cited lines in the current
    checkout and return the most recent SHA's commit
    time, or None on failure.
    """
    try:
        blame = subprocess.run(
            ["git", "blame", "-L", f"{start},{end}", "--", file_path],
            cwd=repo_dir, capture_output=True, text=True, encoding="utf-8",
            timeout=10,
        )
    except (subprocess.TimeoutExpired, OSError):
        return None
    if blame.returncode != 0 or not blame.stdout.strip():
        return None
    shas = set()
    for line in blame.stdout.splitlines():
        m = re.match(r"^(\w+)", line)
        if m:
            shas.add(m.group(1))
    if not shas:
        return None
    most_recent = 0
    for sha in shas:
        try:
            log = subprocess.run(
                ["git", "log", "-1", "--format=%ct", sha],
                cwd=repo_dir, capture_output=True, text=True, encoding="utf-8",
                timeout=5,
            )
        except (subprocess.TimeoutExpired, OSError):
            continue
        if log.returncode == 0 and log.stdout.strip():
            try:
                most_recent = max(most_recent, int(log.stdout.strip()))
            except ValueError:
                continue
    if most_recent == 0:
        return None
    return most_recent


def address_status(file_path, lines, repo_dir, review_time):
    """Return one of: 'addressed', 'open', 'unknown'."""
    touched = line_last_touched(file_path, lines, repo_dir, review_time)
    if touched is None or review_time is None:
        return "unknown"
    if touched > review_time:
        return "addressed"
    return "open"


def fmt_digest(review, findings, action_total, repo_dir=None):
    """Format a Markdown digest for one review.

    When `repo_dir` is the path to a git checkout of the
    repo, the digest also cross-references each finding's
    file:line with `git blame` to mark it as addressed
    (touched after the review) or open (last edit
    pre-dates the review).
    """
    sev_counts = Counter(f["severity"] for f in findings)
    file_counts = Counter(f["file"] for f in findings)
    state = review.get("state", "COMMENTED")
    submitted = review.get("submitted_at", "?")
    review_time = parse_submitted_at(review)
    can_address = bool(repo_dir) and review_time is not None

    if can_address:
        for f in findings:
            f["status"] = address_status(f["file"], f["lines"], repo_dir, review_time)
        open_findings = [f for f in findings if f["status"] == "open"]
        addressed_findings = [f for f in findings if f["status"] == "addressed"]
    else:
        for f in findings:
            f["status"] = "unknown"
        open_findings = addressed_findings = []

    lines = []
    lines.append(f"### CodeRabbit review \u2014 state `{state}`, submitted `{submitted}`")
    lines.append("")

    # Headline
    parts = []
    for sev in ("Major", "Minor", "Trivial", "Nitpick", "Unknown"):
        if sev_counts.get(sev):
            parts.append(f"{sev_counts[sev]} {sev}")
    headline = "Actionable: " + (", ".join(parts) if parts else "none")
    if action_total is not None and action_total != len(findings):
        headline += f"  (header says {action_total})"
    if can_address:
        headline += (
            f"  |  Addressed: {len(addressed_findings)}, Open: {len(open_findings)}"
            f"  (of {len(findings) - len(open_findings) - len(addressed_findings)} unknown)"
        )
    lines.append(headline)
    lines.append("")

    # Findings table (severity-ordered, then file)
    if findings:
        cols = ["Severity", "File", "Lines", "Title"]
        if can_address:
            cols.append("Addressed?")
        sep = "|" + "|".join(["---"] * len(cols)) + "|"
        lines.append("| " + " | ".join(cols) + " |")
        lines.append(sep)
        sev_emoji = {
            "Major": "\U0001f7e0 Major",
            "Minor": "\U0001f7e1 Minor",
            "Trivial": "\U0001f535 Trivial",
            "Nitpick": "\U0001f7e3 Nitpick",
            "Unknown": "❔ Unknown",
        }
        for f in sorted(findings, key=lambda x: (SEV_RANK.get(x["severity"], 99), x["file"], x["lines"])):
            row = [
                sev_emoji.get(f["severity"], f["severity"]),
                f"`{f['file']}`",
                f"`{f['lines']}`",
                f["title"].rstrip("."),
            ]
            if can_address:
                row.append(_status_glyph(f["status"]))
            lines.append("| " + " | ".join(row) + " |")
        lines.append("")

    # Open findings (only when addressing is enabled)
    if can_address and open_findings:
        lines.append("**Still open:**")
        for f in open_findings:
            lines.append(f"- `{f['file']}:{f['lines']}` \u2014 {f['title']}")
        lines.append("")

    # File rollup (most-touched first)
    if file_counts:
        lines.append("**Files touched in review (most findings first):**")
        for path, n in file_counts.most_common():
            lines.append(f"- `{path}` \u2014 {n} finding{'s' if n != 1 else ''}")
        lines.append("")

    # Section rollup
    section_counts = Counter(f["section"] for f in findings if f["section"])
    if section_counts:
        sec_str = ", ".join(f"{v} {k}" for k, v in section_counts.most_common())
        lines.append(f"**Section split:** {sec_str}")
        lines.append("")

    # TL;DR
    if findings:
        majors = [f for f in findings if f["severity"] == "Major"]
        if majors:
            tldr = f"{len(majors)} Major finding(s) need a code change"
        elif sev_counts.get("Unknown") and not sev_counts.get("Trivial") and not sev_counts.get("Nitpick"):
            tldr = "All findings are Unknown severity (rate-limited walkthrough mode)"
        elif sev_counts.get("Trivial"):
            tldr = "No Majors; Trivial-only \u2014 most are doc/comment fixes"
        else:
            tldr = "All findings are minor"
        if can_address and open_findings:
            tldr += f"; {len(open_findings)} still open against current branch"
        lines.append(f"**TL;DR:** {tldr}.")
        lines.append("")

    return "\n".join(lines)


def _status_glyph(status):
    if status == "addressed":
        return "yes"
    if status == "open":
        return "no"
    return "?"


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    p.add_argument("--pr", type=int, required=True, help="PR number")
    p.add_argument("--repo", default="sampathmannam/MindAnchor")
    p.add_argument(
        "--repo-dir",
        help="Local git checkout of --repo; enables the "
        "Addressed? column by cross-referencing each finding's "
        "file:line with `git blame` against the review's "
        "submitted_at.",
    )
    p.add_argument("--run", help="CodeRabbit run ID to filter (e.g. b53d744a-...)")
    p.add_argument("--all", action="store_true", help="Show every CodeRabbit review, oldest first")
    p.add_argument("--json", action="store_true", help="Output raw findings as JSON instead of Markdown")
    args = p.parse_args()

    reviews = load_reviews_for(args.repo, args.pr)
    if not reviews:
        print("(no CodeRabbit reviews on this PR)", file=sys.stderr)
        sys.exit(0)

    if args.run:
        reviews = [r for r in reviews if args.run in (r.get("body") or "")]
        if not reviews:
            print(f"(no review found with run id {args.run})", file=sys.stderr)
            sys.exit(1)

    # `load_reviews_for` already returns a time-ordered
    # list, so the most recent review is the last one.

    if args.json:
        out = []
        for r in reviews:
            body = r["body"] or ""
            findings = parse_review(body)
            # Run the same addressed/open check the
            # Markdown path runs, so consumers of the
            # JSON output get the same verdicts the chat
            # digest shows.
            review_time = parse_submitted_at(r)
            if args.repo_dir and review_time is not None:
                for f in findings:
                    f["status"] = address_status(
                        f["file"], f["lines"], args.repo_dir, review_time
                    )
            out.append({
                "id": r["id"],
                "state": r["state"],
                "submitted_at": r["submitted_at"],
                "actionable_total": action_count(body),
                "via_issue_comment": r.get("via_issue_comment", False),
                "findings": findings,
            })
        print(json.dumps(out, indent=2, ensure_ascii=False))
        return

    if args.all:
        for r in reviews:
            body = r["body"] or ""
            findings = parse_review(body)
            print(fmt_digest(r, findings, action_count(body), repo_dir=args.repo_dir))
            print()
        return

    # Default: most recent review only
    r = reviews[-1]
    body = r["body"] or ""
    findings = parse_review(body)
    print(fmt_digest(r, findings, action_count(body), repo_dir=args.repo_dir))


if __name__ == "__main__":
    main()
