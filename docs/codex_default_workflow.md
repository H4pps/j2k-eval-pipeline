# Codex Default Workflow

Use this prompt when you want Codex to handle substantial work without naming
every agent manually:

```text
Use the default workflow for this repository.

Plan with the main gpt-5.5 thread first. Use fast_explorer or deep_explorer only
for bounded read-only investigation when it helps. Use code_writer for focused
implementation with clear file ownership. Use fast_verifier for assigned Gradle
tests, builds, lint, formatting, and report-generation checks. Finish by
reviewing the diff against AGENTS.md, docs/code_review.md, and the assignment
requirements.

Before editing, state the intended write scope. After editing, summarize what
changed, why, which verification commands ran, and remaining risks.
```

Short trigger:

```text
Use the default workflow and handle this end to end.
```

Notes:

- The main thread remains responsible for planning and final judgment.
- Subagents should be used only for bounded work that benefits from separation
  or parallelism.
- Avoid multiple agents editing the same files in parallel.
- For small one-file changes, the main thread can implement directly.
