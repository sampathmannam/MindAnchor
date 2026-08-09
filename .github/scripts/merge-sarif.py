#!/usr/bin/env python3
"""
Merge multiple SARIF 2.1.0 files into one.

Used by .github/workflows/detekt.yml. detekt writes
a separate SARIF file per Gradle module under each
module's build/reports/detekt/ directory; the
upload-sarif GitHub Action expects a single file.

Usage:
    python3 merge-sarif.py <output> <input1> [input2 ...]

The merged file's $schema is the SARIF 2.1.0 schema;
the `runs` list is the concatenation of every input
file's `runs`. The result is a valid SARIF document
for the GitHub code-scanning uploader.
"""
import json
import sys


def main() -> int:
    if len(sys.argv) < 3:
        print(f"usage: {sys.argv[0]} <output> <input1> [input2 ...]",
              file=sys.stderr)
        return 1
    output = sys.argv[1]
    inputs = sys.argv[2:]
    merged = {
        "runs": [],
        "version": "2.1.0",
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
    }
    for path in inputs:
        with open(path) as fh:
            data = json.load(fh)
        runs = data.get("runs", [])
        if not isinstance(runs, list):
            print(f"{path}: 'runs' is not a list", file=sys.stderr)
            return 1
        merged["runs"].extend(runs)
    with open(output, "w") as fh:
        json.dump(merged, fh, indent=2)
    print(f"Merged {len(inputs)} SARIF file(s) into {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
