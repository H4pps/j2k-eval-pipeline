# Evaluation Metrics

This document defines the exact metrics emitted by the evaluator in
`build/reports/j2k-eval/<benchmark>/<kind>/evaluation.json` and the matching
`summary.md`. These are evaluator metrics, not raw converter execution metrics:
`build/j2k/<benchmark>/<kind>/conversion.json` only records converter status,
warnings, errors, and output paths.

Metrics are calculated from matched Java/Kotlin file pairs. Missing generated
files are reported under file coverage and are not included in parser-backed
content or nullability scores.

## Metric Labels

Metrics marked `NEW` are not present in the first iteration of the evaluator.

`conversion.json` remains converter execution metadata. Evaluator-derived
quality and preservation metrics belong in `evaluation.json`, `summary.md`, and
comparison reports.

## Current Report Inventory

| Report section | Meaning |
| --- | --- |
| `evaluation.json` | Single evaluator result for one benchmark run. It is now written per converter kind. |
| `benchmark` | Benchmark id, name, role, repository, ref, and source roots. |
| `benchmark.kind` `NEW` | Converter kind evaluated by this report, such as `k1-old-dumb`, `k1-old-smart`, `k1-new`, or `k2`. |
| `paths` | Checkout, generated Kotlin, and report paths. |
| `checkout` | Checkout/build metadata loaded by the evaluator. |
| `conversion` | Converter execution metadata loaded from `conversion.json`. |
| `file_coverage` | File matching and package preservation metrics. |
| `structure` | Parser-visible declaration and API-name preservation metrics. |
| `content` | Body-shape and raw control-flow metrics, extended with `NEW` preservation scores and exact denominators. |
| `nullability` | Java annotation to Kotlin nullable-type metrics, extended with `NEW` nullability operation consistency metrics. |
| `quality` | Kotlin quality warning counters. |
| `analysis` | Warning findings and analysis details used by reports. |
| `counts` | Top-level report count summary. |
| `status` | Evaluator completion status. |
| `warnings` | Human-reviewable evaluator warnings. |
| `comparison.json`, `comparison.md` `NEW` | Per-benchmark comparison reports that transpose per-kind `evaluation.json` metrics. They do not recalculate formulas. |

## File Coverage

File coverage measures whether each configured Java input has a generated
Kotlin output at the expected relative path.

| JSON key | Meaning |
| --- | --- |
| `java_file_count` | Configured Java files discovered under benchmark source roots. |
| `kotlin_file_count` | Generated Kotlin files discovered under the output directory. |
| `matched_kotlin_file_count` | Java inputs with an expected generated Kotlin file. |
| `coverage_percent` | `matched_kotlin_file_count / java_file_count * 100`. |
| `missing_kotlin_files` | Expected Kotlin paths that were not generated. |
| `unexpected_kotlin_files` | Generated Kotlin files that do not match a Java input. |
| `empty_generated_files` | Generated Kotlin files whose text is blank. |
| `package_preserved_count` | Matched files whose package declaration and package path match the Java input. |
| `package_preservation_percent` | `package_preserved_count / matched_kotlin_file_count * 100`. |
| `package_mismatch_files` | Matched files whose package declaration or package path differs from Java. |

## Structural Preservation

Structural metrics compare parser-visible declarations and public API names in
matched files. JavaBean accessors that become Kotlin properties are treated as
preserved when the generated property exists on the expected owner.

| JSON key | Meaning |
| --- | --- |
| `java_top_level_declaration_count`, `kotlin_top_level_declaration_count` | Class/interface/enum/object-like declarations. |
| `java_class_like_count`, `kotlin_class_like_count` | Java classes/records and Kotlin classes. |
| `java_interface_count`, `kotlin_interface_count` | Interface declarations. |
| `java_enum_count`, `kotlin_enum_count` | Enum declarations. |
| `java_method_count`, `kotlin_function_count` | Distinct method/function names found by parsers. |
| `public_api_name_overlap_count` | Shared public names, including JavaBean accessors backed by Kotlin properties. |
| `missing_public_api_names` | Java public names not represented in Kotlin. |
| `kotlin_only_public_api_names` | Kotlin public names not present in Java. |
| `name_diffs` | Detailed class/interface/enum/object/function name differences, including JavaBean accessor mapping details. |

## Content Preservation

Content metrics compare parser-backed method/function body and control-flow
signals in matched files.

### Raw Counts

| JSON key | Meaning |
| --- | --- |
| `matched_file_count` | Matched file pairs included in content metrics. |
| `java_non_empty_method_count`, `kotlin_non_empty_function_count` | Methods/functions with non-empty bodies. |
| `java_empty_method_count`, `kotlin_empty_function_count` | Methods/functions with explicit empty bodies. |
| `missing_kotlin_bodies` | Java non-empty methods not represented by Kotlin bodies or Kotlin properties. |
| `content_shape_mismatch_files` | Files where Kotlin lost a Java body-shape signal. |
| `java_return_count`, `kotlin_return_count` | Return statements. |
| `java_branch_count`, `kotlin_branch_count` | Java `if`/`switch` and Kotlin `if`/`when` expressions. |
| `java_loop_count`, `kotlin_loop_count` | Loop constructs. |
| `java_throw_count`, `kotlin_throw_count` | Throw statements/expressions. |
| `java_try_count`, `kotlin_try_count` | Try blocks/expressions. |
| `java_function_declaration_count`, `kotlin_function_declaration_count` `NEW` | Total parser-visible method/function declarations used as density denominators. |
| `content_shape_preserved_file_count` `NEW` | `matched_file_count - content_shape_mismatch_file_count`. |
| `content_shape_mismatch_file_count` `NEW` | Count of `content_shape_mismatch_files`, added for direct report auditability. |

### Control Flow Fidelity Score

Control Flow Fidelity Score measures how well generated Kotlin preserves Java
returns, branches, throws, and tries.

```text
preservation = if java_count == 0 then 1.0 else min(kotlin_count / java_count, 1.0)

CFFS =
  return_preservation * 0.4 +
  branch_preservation * 0.3 +
  throw_preservation * 0.2 +
  try_preservation * 0.1
```

| JSON key | Meaning |
| --- | --- |
| `return_preservation_ratio` `NEW` | Capped `kotlin_return_count / java_return_count`. |
| `branch_preservation_ratio` `NEW` | Capped `kotlin_branch_count / java_branch_count`. |
| `throw_preservation_ratio` `NEW` | Capped `kotlin_throw_count / java_throw_count`. |
| `try_preservation_ratio` `NEW` | Capped `kotlin_try_count / java_try_count`. |
| `control_flow_fidelity_score` `NEW` | Weighted score above. |

### Content Shape Preservation Rate

Content Shape Preservation Rate is the share of matched files that do not have
content-shape mismatches.

```text
content_shape_preservation_rate =
  if matched_file_count == 0 then 1.0
  else content_shape_preserved_file_count / matched_file_count
```

| JSON key | Meaning |
| --- | --- |
| `content_shape_preservation_rate` `NEW` | Formula above, scored on a `0.0` to `1.0` scale. |

### Return Statement Density

Return density compares how many return statements each language has per
method/function declaration.

```text
java_return_density = java_return_count / java_function_declaration_count
kotlin_return_density = kotlin_return_count / kotlin_function_declaration_count

return_statement_density_preservation =
  if java_return_density == 0 then 1.0
  else min(kotlin_return_density / java_return_density, 1.0)
```

| JSON key | Meaning |
| --- | --- |
| `java_return_density` `NEW` | Java `return_count / function_declaration_count`. |
| `kotlin_return_density` `NEW` | Kotlin `return_count / function_declaration_count`. |
| `return_statement_density_preservation` `NEW` | Capped Kotlin return density divided by Java return density. |

### Branch Complexity Index

Branch complexity approximates conditional complexity per method/function.

```text
java_branch_complexity_index =
  (java_branch_count + java_loop_count + java_try_count) / java_function_declaration_count

kotlin_branch_complexity_index =
  (kotlin_branch_count + kotlin_loop_count + kotlin_try_count) / kotlin_function_declaration_count

branch_complexity_index_preservation =
  if java_branch_complexity_index == 0 then 1.0
  else min(kotlin_branch_complexity_index / java_branch_complexity_index, 1.0)
```

The BCI preservation cap is `1.0` so additional generated branches do not score
above perfect preservation.

| JSON key | Meaning |
| --- | --- |
| `java_branch_complexity_index` `NEW` | Java `(branch_count + loop_count + try_count) / function_declaration_count`. |
| `kotlin_branch_complexity_index` `NEW` | Kotlin `(branch_count + loop_count + try_count) / function_declaration_count`. |
| `branch_complexity_index_preservation` `NEW` | Capped Kotlin branch complexity index divided by Java branch complexity index. |

## Nullability Signals

Nullability metrics compare Java nullability annotations with generated Kotlin
nullable types and also measure internally contradictory Kotlin nullability
patterns.

### Annotation Preservation

| JSON key | Meaning |
| --- | --- |
| `java_nullable_annotation_count` | Java `@Nullable`/`@CheckForNull` annotation count. |
| `java_not_null_annotation_count` | Java `@NotNull`/`@Nonnull` annotation count. |
| `kotlin_nullable_type_count` | Kotlin function/property/parameter nullable type count. |
| `nullable_annotations_not_preserved` | Java nullable declarations not mapped to nullable Kotlin declarations. |
| `not_null_annotations_became_nullable` | Java not-null declarations mapped to nullable Kotlin declarations. |

### Nullability Inference Accuracy

Nullability Inference Accuracy penalizes Kotlin patterns where a variable is
cast to a non-null type and then checked against `null` shortly afterward in the
same function.

```text
total_nullability_operation_count =
  null_comparison_count + nullability_cast_count + safe_call_count

non_contradictory_nullability_operation_count =
  total_nullability_operation_count - contradictory_nullability_patterns

nullability_inference_accuracy =
  if total_nullability_operation_count == 0 then 1.0
  else non_contradictory_nullability_operation_count / total_nullability_operation_count
```

| JSON key | Meaning |
| --- | --- |
| `contradictory_nullability_patterns` `NEW` | Non-null `as Type` casts followed by `== null` or `!= null` on the same variable within 10 lines in the same function. |
| `null_comparison_count` `NEW` | Kotlin `== null` and `!= null` comparisons. |
| `nullability_cast_count` `NEW` | Kotlin `as` and `as?` casts. |
| `safe_call_count` `NEW` | Kotlin safe calls, `?.`. |
| `total_nullability_operation_count` `NEW` | Denominator for NIA. |
| `nullability_inference_accuracy` `NEW` | Score above, on a `0.0` to `1.0` scale. |

The contradiction detector is syntax-based Kotlin PSI analysis, not semantic
type analysis.

## Kotlin Quality Warnings

Quality metrics are negative review signals in generated Kotlin. They are
separate from core preservation scores so post-processing dirtiness can be read
apart from structural conversion fidelity.

| JSON key | Meaning |
| --- | --- |
| `todo_count` | `TODO()` calls. |
| `not_null_assertion_count` | `!!` assertions. |
| `not_null_assertion_in_call_count` | `!!` assertions inside function-call arguments. |
| `any_nullable_count` | `Any?` types. |
| `unresolved_import_count` | Imports containing unresolved-looking markers. |
| `java_interop_reference_count` | Java interop leftovers such as `java.util.*` utility references. |
| `getter_setter_call_count` | Java-style getter/setter calls left in Kotlin. |
| `nullable_boolean_comparison_count` | Safe-call boolean comparisons such as `x?.flag != true`. |
| `eager_property_initialization_count` | `val` initializers that eagerly call getters or read call results. |

## Comparison Reports

`comparison.json` and `comparison.md` are `NEW`. They read metrics from
per-kind `evaluation.json` files, transpose evaluator metrics across converter
kinds, and show `-` when an older evaluation report does not contain a newer
key. They do not recalculate formulas; the per-kind evaluator report remains
the source of truth.

| Comparison output | Meaning |
| --- | --- |
| `comparison.json` `NEW` | Machine-readable per-kind comparison built from `evaluation.json` inputs. |
| `comparison.md` `NEW` | Markdown comparison summary for humans. |
| `kinds` `NEW` | Converter kinds included in the comparison. |
| `missing_kinds` `NEW` | Expected converter kinds whose `evaluation.json` was not available. |
| `low_coverage_kinds` `NEW` | Converter kinds flagged for low file coverage. |
| Metric comparison sections `NEW` | File coverage, structural preservation, content preservation, nullability, and quality metrics transposed across kinds. |
