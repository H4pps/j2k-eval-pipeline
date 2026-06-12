# Edge-Case Converter Comparison: K1-old-dumb vs K1-new vs K2

This report compares the current local generated Kotlin for the custom
J2K edge-case dataset across three converter kinds:

- `k1-old-dumb` - version used in the first iteration of the eval
- `k1-new` - latest K1 converter using the same workflow as the k2 (but now k1-old)
- `k2`

The comments below are based on generated files under
`build/j2k/j2k-edge-cases/<kind>/generated-kotlin/org/example/edgecases/`.
Those generated artifacts are evidence only and are not checked in.

## Current Local Summary

| Metric | k1-old-dumb | k1-new | k2 |
| --- | ---: | ---: | ---: |
| Generated Kotlin files | 17 | 17 | 17 |
| Matched Kotlin files | 17 | 17 | 17 |
| Content-shape mismatches | 1 | 12 | 3 |
| Not-null assertions (`!!`) | 6 | 2 | 6 |
| Nullable type markers | 8 | 13 | 43 |
| `Any?` types | 0 | 0 | 1 |
| Manual passed cases | 3 | 12 | 9 |
| Manual failed cases | 14 | 5 | 8 |

## Side-By-Side Case Comments

`PASSED` and `FAILED` are manual review labels for this comparison.

K2 also shows a repeated import-cleanup issue in this local run: most generated
edge-case files contain large unrelated import blocks from other edge-case
classes.

| Case | Hypothesis | k1-old-dumb comment | k1-new comment | k2 comment |
| --- | --- | --- | --- | --- |
| Nested anonymous classes | Captured values and nested anonymous objects should survive conversion. | FAILED: keeps nested anonymous objects, but output is rough and uses raw-looking `Callable()` and `Object()` anonymous objects. | PASSED: cleaner Kotlin with a `Callable` lambda plus nested `object : Any()`; capture shape remains readable. | PASSED: similar structure, but broad nullable signatures such as `input: String?` and `String?` returns. |
| Recursive generics and wildcards | Recursive bounds and wildcard producer/consumer APIs should remain usable. | FAILED: preserves the recursive type shape but keeps Java-like calls such as `value.id().length()` and rough collection typing. | FAILED: cleaner collections and formatting, but the recursive bound becomes nullable and `other!!` appears in `copyIdFrom`. | FAILED: most conservative; recursive bounds and ids become broadly nullable, with `other!!`, `value.id()!!`, and mutable lists. |
| SAM/lambda overloads | Overloaded SAM targets should stay distinguishable. | PASSED: keeps explicit lambda casts to `Supplier<String>` and `Callable<String>`, with Java-style `strip()` and `toUpperCase()`. | PASSED: converts to `Supplier { ... }` and `Callable { ... }`; still uses a cast for the callable overload. | PASSED: keeps both overloads but changes SAM types to nullable `Supplier<String?>` and `Callable<String?>`. |
| Annotation-heavy framework style | Framework annotations should keep targets and values. | FAILED: uses Java annotation APIs like `RetentionPolicy` and `ElementType`; formatting is rough and `normalize` contains `text!!` after a null check. | FAILED: rewrites annotation metadata to Kotlin `AnnotationRetention` and `AnnotationTarget`, but still leaves `java.util.List.of(...)` in some calls. | FAILED: same Kotlin annotation target model as K1-new, but changes list/event types to mutable nullable collections. |
| Try-with-resources | Resource close order should survive conversion to `use`. | FAILED: converts to nested `use`, but as dense one-line lambdas; contains `line!!` after a null check and makes `events` type inconsistent. | PASSED: clean nested `use` blocks, `line?.uppercase() ?: ""`, and `List<String>` close-order output. | FAILED: preserves nested `use`, but returns `MutableList<String?>` and makes resource state nullable. |
| Checked exceptions | Checked exception intent should stay visible in Kotlin. | FAILED: preserves `@Throws`, but has `text!!` inside exception-message construction after null handling. | PASSED: preserves `@Throws`; converts `parseOrDefault` to `return try { ... } catch { ... }`, but keeps an impossible null check on non-null `text`. | PASSED: preserves `@Throws`; keeps explicit `return` statements inside `try` and `catch`. |
| Static nested companion-like pattern | Static holder and factory patterns should remain understandable. | PASSED: keeps a nested `Companion` class and companion object, with rough formatting and Java-style string equality. | PASSED: cleaner Kotlin with `require(...)`, trimmed strings, and a companion object singleton. | PASSED: similar to K1-new, but the nested `Companion` constructor stays private and `validateFactoryName` accepts `String?`. |
| Varargs and arrays | Java varargs and arrays need correct Kotlin array handling. | FAILED: keeps non-null `Array<String>` signatures but returns `arrayOfNulls<String>(size)` as `Array<String>`. | FAILED: converts arrays to nullable element arrays: `Array<String?>` and `Array<String?>` return. | FAILED: makes both elements and chunks nullable, using `chunk!!` while flattening. |
| JavaBean properties | Java getters and setters should become Kotlin properties. | PASSED: converts getters/setters to properties: `isEnabled`, `url`, and `retryCount`. | PASSED: also converts to properties, but acronym `URL` becomes `uRL`. | PASSED: converts to properties too, with `uRL`; generated KDoc parameter names include `this.isEnabled` and `this.uRL`. |
| Nullability annotations | `@Nullable` and `@NotNull` should be reflected in Kotlin types. | FAILED: keeps annotation names and makes `requireOrFallback` return `String?` despite `@NotNull`. | PASSED: keeps `find` nullable and makes `requireOrFallback` return non-null `String`. | FAILED: broadens the map to `MutableMap<String?, String?>`, makes `@NotNull` parameters nullable, and returns `String?`. |
| Interface getter defaults | Interface getters may become properties while default methods remain valid. | FAILED: converts getters to properties, but with explicit `@get:Override` and rough `super@HasLabel` syntax. | PASSED: clean property overrides and Kotlin `super<HasLabel>.summary()` calls. | FAILED: broken-looking output; `Sample` stores private fields and emits `getLabel()`/`getCode()` overrides instead of clean `override val` properties. |
| Raw types and unchecked casts | Raw collections may produce unsafe casts or broad types. | FAILED: keeps raw `Map` and `List` shapes, uses `List.of()`, and iterates over `unchecked!!`. | PASSED: uses `Map<*, *>`, `List<*>`, `listOf()`, and avoids `!!`. | FAILED: uses mutable nullable shapes: `MutableMap<*, *>`, `MutableList<String?>`, and casts to `MutableList<*>`. |
| Nullable boolean semantics | Boxed Boolean null semantics should survive. | FAILED: converts boxed flags to non-null `Boolean`, losing nullable API shape while keeping identity checks. | FAILED: also uses non-null `Boolean`; keeps Java interop comparisons against `java.lang.Boolean.TRUE/FALSE`. | PASSED: uses nullable `Boolean?` parameters and keeps the Java interop comparisons. |
| Missing nullability annotations | Unannotated Java APIs should not become overconfident Kotlin. | FAILED: keeps map values non-null but returns nullable lookup; `lengthOrDefault` uses `value!!` after a null check. | FAILED: uses idiomatic `value?.length ?: defaultValue`, but `chooseFirst` returns non-null with `fallback!!`. | PASSED: broadens map and parameters to nullable, avoids `!!`, and keeps explicit Java-style null branches. |
| Risky not-null assertions in call arguments | Chained call arguments should avoid risky `!!`. | FAILED: record-like nested classes lose constructor state, while methods still reference `payload` and `name`. | PASSED: converts nested records to `@JvmRecord data class` with non-null fields and no `!!`. | FAILED: converts records too, but makes fields nullable and uses `payload!!` and `name!!`. |
| Records and sealed types | Java records and sealed hierarchy checks should convert cleanly. | FAILED: fails pattern handling with `shape is ???` and missing record constructor state. | PASSED: converts records to `@JvmRecord data class` and pattern checks to `shape is Circle`/`Rectangle`. | PASSED: also converts records and pattern checks, but makes `shape` nullable. |
| Switch expressions and pattern matching | Java 17 switch expressions and pattern variables should lower to Kotlin. | FAILED: leaves `value is ???` placeholders and Java `switch` expression syntax. | PASSED: converts to Kotlin `when` and `is String`/`is Number` checks. | PASSED: also converts to Kotlin `when`, but accepts `Any?`. |

## Result

- `k1-new` is usually the cleanest Kotlin surface output in this dataset.
- `k2` often preserves branch/return/content shape better than `k1-new`, but it
  adds broad nullable types in these local artifacts.
