# Edge-Case Dataset Report

This is the dedicated report for the custom Java edge-case dataset requested by
the assignment.

- Dataset repo: [H4pps/j2k-edge-cases](https://github.com/H4pps/j2k-edge-cases)
- Benchmark config: `benchmarks/j2k-edge-cases.yml`
- Generated Kotlin: `build/j2k/j2k-edge-cases/generated-kotlin/`
- Evaluation report: `build/reports/j2k-eval/j2k-edge-cases/summary.md`

The dataset contains small Java files that each test one converter risk. The
Java project has its own tests, so the Java baseline is known to compile and
behave before J2K runs.

## Result Labels

- Passed: J2K produced Kotlin and no concrete issue was found in the current
  evaluator/manual review.
- Needs review: J2K produced Kotlin, but the output has a warning that should be
  checked by hand.
- Failed: J2K produced Kotlin that appears incomplete, invalid, or clearly
  wrong.

These labels describe the current manual review of generated Kotlin. They do not
change CI behavior: the pipeline still reports conversion data instead of
failing on code quality.

## Run Summary

- Java files: 17
- Generated Kotlin files: 17
- File coverage: 100%
- Package preservation: 100%
- Conversion status: completed
- Evaluation status: completed with warnings
- Quality findings: 9
- Content-shape mismatch files: 1
- Nullability drift findings: 1
- Passed cases after review: 6
- Needs-review cases after review: 1
- Failed cases after review: 10

Every Java file produced a Kotlin file. That is good, but it is not the same as
correct Kotlin. The useful findings are in the per-case results below.

## Case Results

1. Nested anonymous classes

   - Source class: `NestedAnonymousClassesCase`
   - Hypothesis: nested anonymous objects may break captured values or `this`
     references.
   - Result: Passed
   - Why: Kotlin was generated and no current warning was found.

2. Recursive generics and wildcards

   - Source class: `RecursiveGenericsWildcardsCase`
   - Hypothesis: recursive bounds and wildcard APIs may become awkward Kotlin
     projections.
   - Result: Passed
   - Why: Kotlin was generated and no current warning was found.

3. SAM/lambda overloads

   - Source class: `SamLambdaOverloadsCase`
   - Hypothesis: overloaded SAM targets may need explicit Kotlin types.
   - Result: Passed
   - Why: Kotlin was generated and no current warning was found.

4. Annotation-heavy framework style

   - Source class: `AnnotationHeavyFrameworkStyleCase`
   - Hypothesis: framework annotations should keep targets and values after
     conversion.
   - Result: Failed
   - Why: generated annotation declarations look invalid for Kotlin framework
     use. The output uses Java-style annotation targets and Java-style helper
     calls such as `List.of(...)`.

5. Try-with-resources

   - Source class: `TryWithResourcesCase`
   - Hypothesis: resource close order should stay the same after conversion to
     `use`.
   - Result: Failed
   - Why: generated Kotlin uses deeply nested `use` calls, keeps Java-style
     string calls, and produces suspicious collection typing around close-order
     events.

6. Checked exceptions

   - Source class: `CheckedExceptionsCase`
   - Hypothesis: checked exception intent should stay visible in Kotlin.
   - Result: Not very good imo :)
   - Why: `@Throws` was preserved, but generated code includes unnecessary
     `text!!` inside exception-message arguments after a null check.

7. Static nested companion-like pattern

   - Source class: `StaticNestedCompanionLikeCase`
   - Hypothesis: static holder and factory patterns may become awkward Kotlin
     APIs.
   - Result: Passed
   - Why: Kotlin was generated with method bodies and no current warning.

8. Varargs and arrays

   - Source class: `VarargsArraysCase`
   - Hypothesis: Java varargs and arrays need correct Kotlin array and spread
     handling.
   - Result: Failed
   - Why: generated Kotlin builds a nullable array shape where a non-null
     `Array<String>` is expected.

9. JavaBean properties

   - Source class: `JavaBeanPropertiesCase`
   - Hypothesis: Java getters and setters should become Kotlin properties.
   - Result: Passed
   - Why: accessors were converted to Kotlin properties. `getURL`/`setURL`
     became `url`, which is idiomatic Kotlin even though it changes the visible
     acronym spelling.

10. Nullability annotations

    - Source class: `NullabilityAnnotationsCase`
    - Hypothesis: `@Nullable` and `@NotNull` should be reflected in Kotlin
      types.
    - Result: Failed
    - Why: one Java `@NotNull` method became `String?`, and the generated file
      still references annotation names without a clean Kotlin nullability
      translation.

11. Interface getter defaults

    - Source class: `InterfaceGetterDefaultMethodsCase`
    - Hypothesis: interface getters may become properties while default methods
      remain valid.
    - Result: Passed
    - Why: getters became properties and the explicit default-method override
      shape was preserved.

12. Raw types and unchecked casts

    - Source class: `RawTypesUncheckedCastsCase`
    - Hypothesis: legacy raw collections may produce unsafe Kotlin casts or
      broad types.
    - Result: Failed
    - Why: generated Kotlin keeps raw-looking `Map`/`List` shapes, uses
      `List.of()`, and iterates over `unchecked!!`.

13. Nullable boolean semantics

    - Source class: `NullableBooleanSemanticsCase`
    - Hypothesis: boxed Boolean comparisons should keep Java null behavior.
    - Result: Failed
    - Why: Java boxed `Boolean` parameters became non-null `Boolean`, so the
      null semantics being tested were lost.

14. Missing nullability annotations

    - Source class: `MissingNullabilityAnnotationsCase`
    - Hypothesis: unannotated Java APIs should not become overconfident Kotlin.
    - Result: Failed
    - Why: generated Kotlin contains a redundant `!!` after a null check and
      keeps Java-style property access such as `length()`.

15. Risky not-null assertions in call arguments

    - Source class: `RiskyNotNullAssertionInCallCase`
    - Hypothesis: chained expressions passed into calls may produce risky `!!`.
    - Result: Failed
    - Why: manual review found incomplete record-backed output. Generated
      methods refer to record state that is not properly generated.

16. Records and sealed types

    - Source class: `RecordsAndSealedTypesCase`
    - Hypothesis: Java 17 records and sealed types may expose converter gaps.
    - Result: Failed
    - Why: generated Kotlin contains `???` placeholders and missing record
      state.

17. Switch expressions and pattern matching

    - Source class: `SwitchAndPatternMatchingCase`
    - Hypothesis: Java 17 switch expressions and pattern matching may lower
      poorly.
    - Result: Failed
    - Why: generated Kotlin contains `???` placeholders and Java-like switch
      output. The evaluator also reports a body-shape mismatch here.

## Passed Cases

- `NestedAnonymousClassesCase`
- `RecursiveGenericsWildcardsCase`
- `SamLambdaOverloadsCase`
- `StaticNestedCompanionLikeCase`
- `JavaBeanPropertiesCase`
- `InterfaceGetterDefaultMethodsCase`

These passed the current evaluator/manual review, but they stay in the dataset
because they are useful regression cases.

## Semi-failed

- `CheckedExceptionsCase`: keep the generated `@Throws`, but remove redundant
  `!!` inside exception-message arguments if a postprocessing fix is added.

## Failed Cases

- `AnnotationHeavyFrameworkStyleCase`: annotation conversion and Java helper
  calls need Kotlin-specific rewriting.
- `TryWithResourcesCase`: `use` conversion and collection typing need manual
  rewriting.
- `VarargsArraysCase`: nullable-array output is likely invalid.
- `NullabilityAnnotationsCase`: not-null Java API became nullable Kotlin.
- `RawTypesUncheckedCastsCase`: raw collections and `List.of()` need Kotlin
  rewrites.
- `NullableBooleanSemanticsCase`: boxed Boolean null semantics were lost.
- `MissingNullabilityAnnotationsCase`: redundant `!!` and Java-style
  `length()` remained.
- `RiskyNotNullAssertionInCallCase`: generated record-backed code appears
  incomplete.
- `RecordsAndSealedTypesCase`: generated `???` placeholders and missing record
  state.
- `SwitchAndPatternMatchingCase`: generated `???` placeholders and Java-like
  switch output.

These are the strongest edge-case findings. They show that a successful file
conversion does not always mean usable Kotlin.
