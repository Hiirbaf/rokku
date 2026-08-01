#!/usr/bin/env python3
"""Replace the "## [Unreleased]" section body of CHANGELOG.md with freshly generated content.

Everything between the "## [Unreleased]" heading and the next "## [" heading is replaced.
The rest of the file (already-released, hand-polished sections) is left untouched.
"""
import sys

changelog_path, draft_path = sys.argv[1], sys.argv[2]

changelog = open(changelog_path, encoding="utf-8").read()
draft = open(draft_path, encoding="utf-8").read().strip()

heading = "## [Unreleased]"
start = changelog.index(heading) + len(heading)
rest = changelog[start:]
next_heading = rest.find("\n## [")
end = start + next_heading if next_heading != -1 else len(changelog)

new_body = "\n\n" + draft + "\n" if draft else "\n"
changelog = changelog[:start] + new_body + changelog[end:]

open(changelog_path, "w", encoding="utf-8").write(changelog)
