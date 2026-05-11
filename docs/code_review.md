# Code Review Policy

Use this checklist for Codex `/review` sessions and manual review before commits.

## Priority Order

1. Assignment requirements: Kotlin-only evaluator logic, CI J2K integration,
   reproducible local instructions, comparative analysis, and summary reports.
2. Correctness: metrics must describe actual conversion outputs and avoid
   silently treating missing files or failed converter runs as success.
3. Reproducibility: benchmark revisions, submodule SHAs, generated output paths,
   and local commands must be deterministic and documented.
4. Tests: evaluator parsing, scoring, report generation, and failure handling
   should have focused tests.
5. Security: do not commit secrets, private tokens, local machine paths that are
   required for CI, or generated artifacts that hide their provenance.
6. Maintainability: prefer small Kotlin functions with clear names,
   documentation, and structured logging.

## Expected Checks

- `./gradlew test`
- `./gradlew build`
- Any future formatter, linter, coverage, or report-generation tasks added to
  the Gradle build.

## Reviewer Notes

- Treat converter failures as data when the pipeline ran correctly; do not hide
  them unless the assignment infrastructure itself failed.
- Prefer concrete file and line references in findings.
- Keep style-only feedback below behavioral, testing, and reproducibility issues.
