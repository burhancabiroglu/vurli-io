"""Prints failed JUnit tests as GitHub Actions error annotations.

Job logs need a signed-in user, annotations do not: they show on the run page and
through the public API, so a failure can be diagnosed from anywhere.

Usage: python3 junit-annotations.py <directory with TEST-*.xml>
"""

import pathlib
import sys
import xml.etree.ElementTree as ET

# GitHub shows at most 10 error annotations per step.
LIMIT = 10


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text: str) -> str:
    return escape(text).replace(":", "%3A").replace(",", "%2C")


def main(directory: str) -> None:
    failures = []
    for report in sorted(pathlib.Path(directory).glob("TEST-*.xml")):
        for case in ET.parse(report).getroot().iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                detail = (problem.get("message") or "") + "\n" + (problem.text or "")
                # The first lines carry the assertion and where it failed.
                detail = "\n".join(detail.strip().splitlines()[:25])
                failures.append((f"{case.get('classname')}.{case.get('name')}", detail))
    for title, detail in failures[:LIMIT]:
        print(f"::error title={escape_property(title)}::{escape(detail)}")
    print(f"{len(failures)} failed test(s)")


if __name__ == "__main__":
    main(sys.argv[1])
