# K2 Import Insertion Errors

## Bug

K2 post-processing sometimes fails while inserting imports into generated Kotlin
files. When this happens, references that would normally be imported and
shortened can remain fully qualified.

## Evidence

Latest GitHub Actions logs show `K2J2KPostProcessor` import insertion failures:

| Job | Occurrences |
| --- | ---: |
| `hikaricp - k2` | 14 |
| `spring-petclinic - k2` | 9 |
| `j2k-edge-cases - k2` | 0 |

Representative log lines:

```text
SEVERE - @org.jetbrains.kotlin.j2k.K2J2KPostProcessor -
Trying to insert import com.zaxxer.hikari.util.Credentials into a file
HikariConfig.kt of type class org.jetbrains.kotlin.psi.KtFile with no import list.

SEVERE - @org.jetbrains.kotlin.j2k.K2J2KPostProcessor -
Trying to insert import java.io.Serializable into a file BaseEntity.kt
of type class org.jetbrains.kotlin.psi.KtFile with no import list.
```

K1-old-dumb, K1-new, and K2 also has a smaller instance of the same import-list failure:

```text
WARN - #o.j.k.i.c.ShortenReferences -
Trying to insert import java.util.regex.Pattern into a file PropertyElf.kt
of type class org.jetbrains.kotlin.psi.KtFile with no import list.
```

The issue is therefore not strictly K2-only, but it is much more visible in K2
for the checked CI run.

## Impact

This makes K2 output look dirtier in quality metrics that count verbose
references, Java interop leftovers, or Java-style generated constructs.

In the current local HikariCP artifacts, a coarse fully qualified token scan
shows:

| Kind | Fully qualified token count |
| --- | ---: |
| `k1-new` | 178 |
| `k2` | 623 |
