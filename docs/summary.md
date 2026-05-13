# Evaluation Summary

The README explains how the pipeline is run and where generated files are
written. This file keeps only the current evaluation results and the main review
findings.

## How The Pipeline Works

The pipeline takes a configured Java project, runs IntelliJ's static
Java-to-Kotlin converter on it, and then checks the generated Kotlin output.

For each benchmark, it first clones the configured repository. Then it stages a
copy of the Java sources under `build/` so the original checkout is not
modified. The IntelliJ runner converts those staged Java files to Kotlin and
stores the generated files separately.

After conversion, the Kotlin evaluator compares the original Java files with
the generated Kotlin files. It checks whether files are missing, whether package
and declaration structure was preserved, whether Java methods still have Kotlin
bodies, and whether nullability or Kotlin-quality warnings should be reviewed.

## Benchmark Inputs

| Benchmark | Role | Repository |
| --- | --- | --- |
| HikariCP | Primary real-world benchmark | [H4pps/HikariCP](https://github.com/H4pps/HikariCP) |
| Spring PetClinic | Calibration benchmark | [H4pps/spring-petclinic](https://github.com/H4pps/spring-petclinic) |
| J2K Edge Cases | Custom stress dataset | [H4pps/j2k-edge-cases](https://github.com/H4pps/j2k-edge-cases), documented in [edge_cases.md](edge_cases.md) |

## Result Snapshot

| Benchmark | Conversion | Java files | Kotlin files | Coverage | Missing outputs | Content mismatches | Nullability drift | Quality findings |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| HikariCP | `partial` | 49 | 48 | 97.96% | 1 | 9 | 0 | 81 |
| Spring PetClinic | `completed` | 30 | 30 | 100.00% | 0 | 0 | 0 | 27 |
| J2K Edge Cases | `completed` | 17 | 17 | 100.00% | 0 | 1 | 1 | 9 |

## HikariCP

HikariCP is the most important result because it is the primary benchmark.
Static J2K converted most files, but it failed on one real source file:

- What this tested: whether J2K can handle a real Java library, not only a
  small demo project.
- Hypothesis: reflection-heavy JavaBean code and JDBC/proxy classes are likely
  to expose converter problems.
- Hypothesis: even when files convert, the generated Kotlin may still need review for `!!`, Java interop calls, and body-shape changes.

- Missing generated file: `com/zaxxer/hikari/util/PropertyElf.kt`.
- Converter error: `PropertyElf.java` failed with
  `Unknown primitive type PsiType:null`.

The evaluator did not find missing Kotlin method bodies after JavaBean
getter/setter mapping. It did report body-shape differences and Kotlin quality
risks, mainly Java interop leftovers and not-null assertions.

Interpretation: the pipeline found a concrete converter failure during its run while still producing useful reports for the rest of the codebase.

## Spring PetClinic

Spring PetClinic converted completely:

- What this tested: whether the pipeline works on a familiar Spring MVC/JPA
  application.
- Hypothesis: a small Spring app should convert fully.
- Hypothesis: Spring/JPA annotations should remain attached to the right
  classes, methods, and fields.
- Hypothesis: JavaBean getters and setters should become Kotlin properties,
  not appear as missing methods.

- No missing generated Kotlin files.
- No content-shape mismatch files.
- No nullability annotation drift.

The evaluator still found Kotlin quality warnings

Interpretation: this benchmark is useful as a sanity check that the pipeline can
complete a known Spring-style project.

## J2K Edge Cases

The custom dataset converted completely:

- What this tested: focused Java patterns that are easy to inspect by hand.
- Hypothesis: nested anonymous classes, SAM lambdas, varargs, raw casts,
  records, sealed types, switch expressions, and pattern matching may produce
  awkward Kotlin.
- Hypothesis: nullable booleans and nullability annotations may produce
  nullable-type or `!!` risks.
- Hypothesis: framework-style annotations should keep their targets and values
  after conversion.

- No missing generated Kotlin files.
- One file had content-shape mismatch warnings.
- One not-null Java declaration became nullable Kotlin.
- Manual review found 6 passed cases, 1 review-only case, and 10 failed cases.

Interpretation: the dataset is useful for focused converter review. It gives
small, inspectable examples for raw/unchecked casts, switch and pattern matching
structure, annotation-heavy code, and nullability preservation.
