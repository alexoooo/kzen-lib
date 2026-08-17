# kzen-lib — AI agent guide

## Purpose

kzen-lib is the **context-management core** of the kzen stack. It defines declarative object graphs (parse → type → instantiate), a CQRS store for mutating them, and an SPI for plugging in custom object types. Every other sibling (`kzen-auto`, `kzen-project`, `kzen-launcher`, transitively `kzen-shell`) consumes it.

**Read [`docs/architecture.md`](docs/architecture.md) before doing anything substantive here** — the three-layer model (Notation → Definition → Instance), the suffix conventions (`Notation`/`Definition`/`Instance`/`Metadata`/`Location`/`Command`/`Event`/`Reducer`/`Store`/`Creator`/`Definer`), and the CQRS flow are non-obvious from a cold read.

## Module layout

Four Gradle subprojects:

- **`kzen-lib-common`** — Kotlin Multiplatform. `commonMain` holds the bulk of the code (model + service + api); `jvmMain` and `jsMain` provide platform-specific bits (notably `tech.kzen.lib.platform.ClassName`, persistent collections, datetime).
- **`kzen-lib-jvm`** — JVM-only artifacts and integration points. Tests under `src/test`.
- **`kzen-lib-js`** — JS-only artifacts. Tests under `src/jsTest`.
- **`kzen-lib-reflect-ksp`** — the KSP processor behind `@Reflect` codegen. Pure JVM; consumed as `ksp("tech.kzen.lib:kzen-lib-reflect-ksp:…")` by downstream builds (e.g. kzen-project).

All four publish to mavenLocal at the current source version; downstream siblings reference the KMP modules as `tech.kzen.lib:kzen-lib-common`, `…-jvm`, `…-js` (the `-jvm` / `-js` variant-suffix coords are pinned via `Dependencies.kt` in each consumer).

## Entry points

kzen-lib is a library — no `main`. The most-touched API surfaces:

- **Define a custom object type**: implement `ObjectDefiner` and/or `ObjectCreator` from `kzen-lib-common/.../api/`.
- **Load a graph**: build a `GraphStructure`, hand it to `GraphDefiner.define()`, then `GraphCreator.create()`. See [`docs/architecture.md` § Document load flow](docs/architecture.md#document-load-flow).
- **Mutate a graph**: construct a `NotationCommand` subtype, hand it to a `LocalGraphStore` (`DirectGraphStore` is the in-process impl).
- **Subscribe to changes**: register a `LocalGraphStore.Observer`.

## Key directories

| Path (under `kzen-lib-common/src/commonMain/kotlin/tech/kzen/lib/common/`) | What lives here |
|----|----|
| `api/` | SPI interfaces: `ObjectDefiner`, `ObjectCreator`, `AttributeDefiner`, `AttributeCreator` |
| `model/structure/notation/` | Parsed document tree (`GraphNotation`, `DocumentNotation`, `ObjectNotation`, `AttributeNotation`) + `cqrs/` (commands/events) |
| `model/definition/` | Typed analysis (`GraphDefinition`, `ObjectDefinition`, `*Attempt`) |
| `model/instance/` | Runtime objects (`GraphInstance`, `ObjectInstance`) |
| `model/location/` | `ObjectLocation`, `AttributeLocation`, `ObjectReference` |
| `service/context/` | `GraphDefiner`, `GraphCreator` — orchestrate the layer transitions |
| `service/notation/` | `NotationReducer` — only place commands are applied |
| `service/store/` | `LocalGraphStore`, `DirectGraphStore`, `RemoteGraphStore` |
| `service/parse/`, `service/media/`, `service/metadata/` | Parse, I/O, reflection |
| `objects/` | Bootstrap definers/creators (`DefaultConstructor*`) |

## Build & test

```powershell
# From kzen-lib root (NOT the umbrella — root tasks like publishToMavenLocal need to run on the subprojects)
./gradlew build
./gradlew publishToMavenLocal

# Subproject-specific
./gradlew :kzen-lib-common:jvmTest
./gradlew :kzen-lib-js:jsTest
```

After bumping the Kotlin version, follow the toolchain-bump checklist in [`../kzen/AGENTS.md`](../kzen/AGENTS.md) (`kotlinUpgradeYarnLock`, publish order, `FormulaStepTest` canary).

### Test topology

Tests mirror the package of the code under test (CC-13), and a test that only needs commonMain APIs lives in `kzen-lib-common/src/commonTest` so it runs on **both** JVM and JS. `kzen-lib-jvm/src/test` is only for tests that genuinely need the JVM — real file I/O (`FileNotationMedia`, `GradleLocator`), JDK reflection (`ReflectiveClassMirror`, the `@Reflect` fixture graph reached through `JvmGraphTestUtils.readNotation()`), threads, or the `RunEngine`. Those still mirror the package of what they cover, so `tech.kzen.lib.common.*` test packages exist inside `kzen-lib-jvm` for commonMain units that can only be exercised from the JVM.

`kotlin.test` is the only assertion API — no JUnit4 annotations or `org.junit.Assert`. Suspend entry points use `runTest` (kotlinx-coroutines-test, a `commonTest` dependency), never `runBlocking`, which would pin the test to the JVM.

## Gotchas

- **Variant-suffix coords route through mavenLocal.** Bump the version → `publishToMavenLocal` (all four subprojects) → consumer can compile; skip the publish and any non-umbrella consumer build breaks. Mechanics: [`../kzen/AGENTS.md`](../kzen/AGENTS.md) KMP variant-suffix gotcha.
- **`kzen-lib-reflect-ksp` build wiring — don't undo without understanding why.** The processor module pins `jvmTarget = 17` (`kspProcessorJavaVersion` in its build.gradle.kts) because KSP's analysis-API worker JVM rejects newer class-file versions (symptom if regressed: `class file version NN.0 … only recognizes … up to 65.0`). The processor returns early when it collected zero `@Reflect` classes — without that, an empty `Kzen*Module` lands in a test source set and shadows the main one on the test classpath. KSP commonMain output needs explicit `dependsOn("kspCommonMainKotlinMetadata")` on every `KotlinCompilationTask` *and* on `sourcesJar`/`*SourcesJar` tasks (KSP2 does not auto-wire KMP per-target tasks or sources-jar packaging). And KSP tasks need `--no-configuration-cache` under Gradle 9 (`error writing value of type '[Ljava.lang.Object;'`). Standalone consumer builds (kzen-auto, kzen-project) resolve the processor as `ksp("tech.kzen.lib:kzen-lib-reflect-ksp:$kzenLibVersion")` from mavenLocal — publish it with the rest.
- **commonMain depends only on `platform/`.** Don't reach for `java.*` or browser globals from `commonMain`; the type lives in `platform/jvmMain` / `platform/jsMain` with a matching `expect` in `commonMain`.
- **Composite-build umbrella context** — see [`../kzen/AGENTS.md`](../kzen/AGENTS.md) for toolchain pins (Kotlin 2.4.0, JVM 26, kotlin-wrappers ceiling), the IntelliJ run/debug Provided-scope bug, and the umbrella↔mavenLocal interplay.

## Pointers

- **Foundational concepts** → [`docs/architecture.md`](docs/architecture.md) (read this first).
- **Composite build + toolchain** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **Consumers** → `../kzen-auto/AGENTS.md`, `../kzen-project/AGENTS.md`, `../kzen-launcher/AGENTS.md`.
