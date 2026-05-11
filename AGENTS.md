# kzen-lib — AI agent guide

## Purpose

kzen-lib is the **context-management core** of the kzen stack. It defines declarative object graphs (parse → type → instantiate), a CQRS store for mutating them, and an SPI for plugging in custom object types. Every other sibling (`kzen-auto`, `kzen-project`, `kzen-launcher`, transitively `kzen-shell`) consumes it.

**Read [`docs/architecture.md`](docs/architecture.md) before doing anything substantive here** — the three-layer model (Notation → Definition → Instance), the suffix conventions (`Notation`/`Definition`/`Instance`/`Metadata`/`Location`/`Command`/`Event`/`Reducer`/`Store`/`Creator`/`Definer`), and the CQRS flow are non-obvious from a cold read.

## Module layout

Three Gradle subprojects:

- **`kzen-lib-common`** — Kotlin Multiplatform. `commonMain` holds the bulk of the code (model + service + api); `jvmMain` and `jsMain` provide platform-specific bits (notably `tech.kzen.lib.platform.ClassName`, persistent collections, datetime).
- **`kzen-lib-jvm`** — JVM-only artifacts and integration points. Tests under `src/test`.
- **`kzen-lib-js`** — JS-only artifacts. Tests under `src/jsTest`.

All three publish to mavenLocal at `0.29.1-SNAPSHOT`; downstream siblings reference them as `tech.kzen.lib:kzen-lib-common`, `…-jvm`, `…-js` (the `-jvm` / `-js` variant-suffix coords are pinned via `Dependencies.kt` in each consumer).

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

After bumping the Kotlin version (even patch versions), run `./gradlew kotlinUpgradeYarnLock` and commit the regenerated `kotlin-js-store/yarn.lock`.

## Gotchas

- **Variant-suffix coords route through mavenLocal.** Consumers' `jvmMain`/`jsMain` use `tech.kzen.lib:kzen-lib-common-jvm` / `-js`, which Gradle composite substitution does *not* match by project name. They resolve from mavenLocal at the version `Dependencies.kt` pins. **Bump the version → publish → consumer can compile.** Skip the publish step and any non-umbrella consumer build breaks.
- **commonMain depends only on `platform/`.** Don't reach for `java.*` or browser globals from `commonMain`; the type lives in `platform/jvmMain` / `platform/jsMain` with a matching `expect` in `commonMain`.
- **Composite-build umbrella context** — see [`../kzen/AGENTS.md`](../kzen/AGENTS.md) for toolchain pins (Kotlin 2.3.21, JVM 25, kotlin-wrappers ceiling), the IntelliJ run/debug Provided-scope bug, and the umbrella↔mavenLocal interplay.

## Pointers

- **Foundational concepts** → [`docs/architecture.md`](docs/architecture.md) (read this first).
- **Composite build + toolchain** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **Consumers** → `../kzen-auto/AGENTS.md`, `../kzen-project/AGENTS.md`, `../kzen-launcher/AGENTS.md`.
