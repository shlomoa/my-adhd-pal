# Documentation site baseline (red), issue #156

Evidence that TC-D-021..029 fail before the implementation. Red commit: 1e16149 (first commit of the PR `claude/docs-site`)
(tests and catalogue only; no index, no `mkdocs.yml`, no scripts).

Command: `scripts/test/test-docs-site.sh`. Exit 1.

```
FAIL TC-D-021 every docs/**/*.md is reachable from docs/index.md
FAIL TC-D-022 docs/index.md has the planned sections
FAIL TC-D-023 mkdocs.yml: docs_dir, strict, Material theme and Mermaid fence
FAIL TC-D-024 requirements pin every tool with ==
FAIL TC-D-025 scripts/docs-site.sh builds the site in strict mode
FAIL TC-D-025 the site has an index page
FAIL TC-D-026 a broken relative link fails the strict build and names the file
FAIL TC-D-026 removing the broken link is green
FAIL TC-D-027 the built index page shows the sections
FAIL TC-D-027 protocol.md Mermaid diagrams are rendered as diagrams
FAIL TC-D-028 a document not linked from the index is reported by name
FAIL TC-D-029 the build workflow runs scripts/docs-site.sh
FAIL TC-D-029 the script-tests job runs test-docs-site.sh
```

The line "the generated site is ignored by git" already passes: `build/` is in `.gitignore`.
