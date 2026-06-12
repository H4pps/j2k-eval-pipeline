# J2K Evaluation Pipeline

This repo runs IntelliJ's static Java-to-Kotlin converter benchmark.

The main benchmark is
[HikariCP](https://github.com/brettwooldridge/HikariCP), checked out from
[H4pps/HikariCP](https://github.com/H4pps/HikariCP), it was forked in case there will be new changes to the project. Spring PetClinic is used as
a comparison benchmark, and `j2k-edge-cases` is a small custom dataset for
converter stress tests.

## Benchmarks

Benchmarks are cloned during the run from pinned commits.With this approach the new repo for evaluation can be added by creating a new small yaml file.

| Config | Purpose |
| --- | --- |
| `benchmarks/hikaricp.yml` | primary real-world benchmark |
| `benchmarks/spring-petclinic.yml` | comparison benchmark |
| `benchmarks/j2k-edge-cases.yml` | custom edge-case dataset |

## Pipeline

The pipeline is:

```text
checkout -> convert/evaluate each converter kind -> compare
```

- `checkout` clones the benchmark, checks out the pinned commit, and
  writes `build/benchmarks/<id>/checkout.json`.
- `convert` runs static J2K through the IntelliJ runner once per converter kind
  and writes generated Kotlin to `build/j2k/<id>/<kind>/generated-kotlin`.
- `evaluate` compares Java inputs with each kind's generated Kotlin and writes
  per-kind reports to `build/reports/j2k-eval/<id>/<kind>/`.
- `compare` reads the per-kind reports and writes
  `build/reports/j2k-eval/<id>/comparison.{json,md}`.

GitHub Actions runs this flow in `.github/workflows/j2k-eval.yml`.


## Reports

Each converter-kind evaluation writes:

```text
build/reports/j2k-eval/<id>/<kind>/evaluation.json
build/reports/j2k-eval/<id>/<kind>/summary.md
```

Each benchmark comparison writes:

```text
build/reports/j2k-eval/<id>/comparison.json
build/reports/j2k-eval/<id>/comparison.md
```

The evaluator reports:

- file coverage;
- missing or unexpected Kotlin files;
- package preservation;
- structural differences;
- Java getter/setter to Kotlin property mapping;
- body-shape warnings;
- nullability signals;
- Kotlin quality warnings such as `!!`, `TODO()`, `Any?`, and Java interop
  leftovers.

The exact metric formulas and JSON keys are documented in
[docs/evaluation-metrics.md](docs/evaluation-metrics.md).

The current result summary is in [docs/summary.md](docs/summary.md).

## GitHub Actions Artifacts

The workflow uploads two artifact families.

Per-kind artifacts are produced by each `(benchmark, converter kind)` job:

```text
eval-<benchmark>-<kind>
```

Example:

```text
eval-hikaricp-k1-new
eval-j2k-edge-cases-k2
```

Each per-kind artifact preserves the `build/` subtrees for that run:

```text
build/j2k/<benchmark>/<kind>/conversion.json
build/j2k/<benchmark>/<kind>/generated-kotlin/
build/j2k/<benchmark>/<kind>/logs/
build/reports/j2k-eval/<benchmark>/<kind>/evaluation.json
build/reports/j2k-eval/<benchmark>/<kind>/summary.md
```

Use these artifacts when inspecting one converter kind's generated Kotlin,
converter logs, raw conversion metadata, or evaluator report.

Comparison artifacts are produced after all available per-kind reports for a
benchmark are downloaded and compared:

```text
comparison-<benchmark>
```

Example:

```text
comparison-hikaricp
comparison-j2k-edge-cases
```

Each comparison artifact contains:

```text
build/reports/j2k-eval/<benchmark>/comparison.json
build/reports/j2k-eval/<benchmark>/comparison.md
```

Use these artifacts when comparing converter kinds side by side. Comparison
reports are built from the per-kind `evaluation.json` files; they do not include
generated Kotlin sources.

## Run Locally

The easiest way to reproduce the pipeline locally is to use the Docker runner.
Docker must be running.

Run one benchmark with Docker:

```bash
./scripts/docker-run-local hikaricp
./scripts/docker-run-local spring-petclinic
./scripts/docker-run-local j2k-edge-cases
```

Run all benchmarks:

```bash
./scripts/docker-run-local all
```

The Docker runner mirrors the host runner. It builds `docker/local-runner/Dockerfile`
from the current repository snapshot, starts a container, and runs
`./scripts/run-j2k-eval "$@" --zip-output /out/<artifact>` inside it.

Run the same pipeline directly on the host:

```bash
./scripts/run-j2k-eval hikaricp
./scripts/run-j2k-eval spring-petclinic
./scripts/run-j2k-eval j2k-edge-cases
./scripts/run-j2k-eval all
```

By default, local runs execute all converter kinds sequentially:

```text
k1-old-dumb, k1-old-smart, k1-new, k2
```

Run only one converter kind:

```bash
./scripts/run-j2k-eval hikaricp --kind k2
./scripts/docker-run-local hikaricp --kind k2
```

Run all converter kinds for each benchmark concurrently, then compare:

```bash
./scripts/run-j2k-eval hikaricp --parallel
./scripts/docker-run-local hikaricp --parallel
```

Parallel mode isolates each kind's staging tree and IntelliJ runtime state under
`build/j2k/<id>/<kind>/`, but it still starts one Gradle/IntelliJ process per
kind, so laptop speedups depend on available CPU, memory, and disk throughput.

For a quick smoke check without conversion:

```bash
./scripts/docker-run-local hikaricp --checkout-only
./scripts/run-j2k-eval hikaricp --checkout-only
```

The first full run can take a while because Gradle and IntelliJ dependencies need
to be downloaded. Local Docker caches are kept under `.local-run/`.

The Docker runner does not create a copied workspace on the host. It exports one
zip file with the generated Kotlin files, logs, and reports:

```text
.local-run/artifacts/j2k-eval-<target>.zip
```

The host runner writes outputs directly under `build/j2k/` and
`build/reports/j2k-eval/`.

## Documentation

- [Evaluation summary](docs/summary.md)
- [Evaluation metrics](docs/evaluation-metrics.md)
- [Edge-case dataset report](docs/edge_cases_old.md)
- [Edge-case converter comparison](docs/edge_cases_k1_old_dumb_vs_k1_new_vs_k2.md)
