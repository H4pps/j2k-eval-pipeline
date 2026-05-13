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
checkout -> convert -> evaluate
```

- `checkout` clones the benchmark, checks out the pinned commit, and
  writes `build/benchmarks/<id>/checkout.json`.
- `convert` runs static J2K through the IntelliJ runner and writes
  generated Kotlin to `build/j2k/<id>/generated-kotlin`.
- `evaluate` compares Java inputs with generated Kotlin and writes reports to
  `build/reports/j2k-eval/<id>/`.

GitHub Actions runs this flow in `.github/workflows/j2k-eval.yml`.


## Reports

Each evaluation writes:

```text
build/reports/j2k-eval/<id>/evaluation.json
build/reports/j2k-eval/<id>/summary.md
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

The current result summary is in [docs/SUMMARY.md](docs/summary.md).

## GitHub Actions Artifacts

The workflow uploads one artifact per benchmark:

```text
j2k-eval-hikaricp
j2k-eval-spring-petclinic
j2k-eval-j2k-edge-cases
```

Artifacts contain generated Kotlin, converter logs, `conversion.json`,
`evaluation.json`, and `summary.md`.

## Run Locally

The easiest way to reproduce the pipeline locally is to use the Docker runner.
Docker must be running.

Run one benchmark:

```bash
./scripts/docker-run-local hikaricp
./scripts/docker-run-local spring-petclinic
./scripts/docker-run-local j2k-edge-cases
```

Run all benchmarks:

```bash
./scripts/docker-run-local all
```

For a quick smoke check without conversion:

```bash
./scripts/docker-run-local hikaricp --checkout-only
```

The script builds `docker/local-runner/Dockerfile` from the current repository
snapshot and then runs the pipeline inside that image. The first full run can
take a while because Gradle and IntelliJ dependencies need to be downloaded.
Local Docker caches are kept under `.local-run/`.

The local runner does not create a copied workspace on the host. It exports one
zip file with the generated Kotlin files, logs, and reports:

```text
.local-run/artifacts/j2k-eval-<target>.zip
```

## Documentation

- [Evaluation summary](docs/summary.md)
- [Edge-case dataset report](docs/edge_cases.md)
