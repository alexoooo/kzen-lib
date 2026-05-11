# kzen-lib architecture

Foundational concepts shared by kzen-auto, kzen-project, kzen-launcher, and (transitively) kzen-shell. If you're editing any of those siblings, the vocabulary here applies.

## One-line summary

kzen-lib is a **declarative, location-addressed object graph** with a **CQRS mutation log**. Documents describe objects; objects declare their own types and dependencies; a runtime instantiates the graph from that description. Every layer of the system (syntax, types, runtime) is keyed by the same address — `ObjectLocation`.

## The three layers

The same object appears in three parallel representations, each in its own subpackage:

```
Notation   →   Definition   →   Instance
(syntax)       (typed)          (runtime)
```

| Layer | Subpackage | Anchor types | What it holds |
|-------|------------|--------------|---------------|
| **Notation** | `model/structure/notation/` | `GraphNotation`, `DocumentNotation`, `ObjectNotation`, `AttributeNotation` | The parsed YAML/text. Strings, nested maps/lists. No type info. |
| **Definition** | `model/definition/` | `GraphDefinition`, `ObjectDefinition` | Result of type analysis: each object has a resolved `ClassName`, typed attribute definitions, a creator reference, and a dependency list. |
| **Instance** | `model/instance/` | `GraphInstance`, `ObjectInstance` | Actual JVM/JS objects, instantiated with their constructor attribute values. |

`AttributeNotation` is sealed:
- `ScalarAttributeNotation` — leaf strings.
- `StructuredAttributeNotation` — maps/lists, recursively containing more `AttributeNotation`.

`GraphStructure` (`model/structure/GraphStructure.kt`) unifies `GraphNotation` (syntax) + `GraphMetadata` (type info) into a single object that the Definition layer consumes.

## Document load flow

```
file → NotationMedia → NotationParser → GraphNotation
                                            ↓
                              NotationMetadataReader → GraphMetadata
                                            ↓
                      GraphNotation + GraphMetadata = GraphStructure
                                            ↓
                          GraphDefiner.define(GraphStructure) → GraphDefinition
                                            ↓
                          GraphCreator.create(GraphDefinition) → GraphInstance
```

Services involved (all under `service/`):

- `service/media/NotationMedia` — abstraction over file I/O (per-platform impls).
- `service/parse/NotationParser` — text → notation tree.
- `service/metadata/NotationMetadataReader` — reflection-driven type extraction.
- `service/context/GraphDefiner` — produces `GraphDefinition`.
- `service/context/GraphCreator` — instantiates `GraphInstance`.

## CQRS

All mutations to the notation layer go through commands and emit events. State is never edited in place.

```
NotationCommand   →   NotationReducer.applyStructural()   →   NotationEvent   →   LocalGraphStore.Observer
   (intent)              (validation + apply)                   (fact)              (subscribers)
```

Key types in `model/structure/notation/cqrs/`:

- `NotationCommand` (sealed) — `StructuralNotationCommand` (create/rename/delete documents, objects, attributes) and `SemanticNotationCommand` (attribute value changes).
- `NotationEvent` — immutable record of what changed; downstream consumers rebuild derived state from this stream.
- `NotationReducer` (`service/notation/NotationReducer.kt`) — the only place commands are applied; produces the event.

Why this matters:

- **Remote sync** — `RemoteGraphStore` ships events over the wire; the local and remote stores converge by replaying the same event log.
- **Auditing / undo** — the event log is a natural history.
- **Observation** — `LocalGraphStore.Observer` lets UI layers (kzen-auto-js, kzen-project-js) react to specific structural changes.

The reference implementation of the store is `service/store/DirectGraphStore` (in-process); `RemoteGraphStore` is the client-side proxy.

## Location-based identity

Every layer is indexed by **`ObjectLocation`** = `DocumentPath` + `ObjectPath`.

- `DocumentPath` — hierarchical document address (`model/document/`).
- `ObjectPath` — `ObjectName` + nesting within a document (`model/obj/`).
- `AttributeLocation` = `ObjectLocation` + `AttributePath` (`model/attribute/`, `model/location/`).

Cross-document references use `ObjectReference` + `ObjectReferenceHost`. The host scopes resolution so a partial reference can resolve to the most-local matching object — this is how documents pull in shared objects without fully-qualified addresses everywhere.

Reasoning about the codebase: when you see a function take an `ObjectLocation`, it works at *every* layer transparently because the three layers are aligned on that key.

## Suffix glossary

These suffixes carry consistent semantic meaning. Reading a type name without knowing the suffix is half-blind.

| Suffix | Means |
|--------|-------|
| `Notation` | Parsed syntax (strings/structure). No types. |
| `Definition` | Type-analyzed layer. Includes `ClassName`, typed attributes. |
| `Instance` | Runtime instantiated object. |
| `Metadata` | Type info extracted via reflection. |
| `Location` | Global address (document + object [+ attribute]). |
| `Path` / `Name` / `Nesting` | Components of a location (hierarchical address parts). |
| `Reference` | Cross-document pointer; resolved against a `ReferenceHost`. |
| `Command` | CQRS mutation intent. |
| `Event` | CQRS mutation fact (post-apply). |
| `Reducer` | Command → Event handler. |
| `Store` | Persistent state + observers. |
| `Creator` / `Definer` | SPI interfaces in `api/` — extension points for custom object types. |
| `Spec` | Static description of a typed attribute (e.g. type constraint). |
| `Attempt` | Definition result that may carry errors (e.g. `GraphDefinitionAttempt`); pattern for partial / fallible analysis. |

## SPI / extension points

`api/` (in `kzen-lib-common/commonMain/kotlin/tech/kzen/lib/common/api/`) contains the extension surface:

- `ObjectDefiner` — converts notation + metadata → `ObjectDefinition` for one object type.
- `ObjectCreator` — instantiates a defined object.
- `AttributeDefiner` / `AttributeCreator` — same split, but for individual typed attributes.

Bootstrap implementations live in `objects/` — `DefaultConstructorObjectDefiner` / `DefaultConstructorObjectCreator` are the fallbacks used for plain Kotlin classes. Downstream siblings register their own definers/creators against this SPI (kzen-auto-plugin is the public SPI surface for third-party plugins).

## Package map

Top-level `tech.kzen.lib.common`:

```
api/         — SPI: ObjectDefiner, ObjectCreator, AttributeDefiner/Creator
codegen/     — code generation helpers
exec/        — execution-layer abstractions
model/
  attribute/ — AttributeName, AttributePath, AttributeNesting
  definition/ — ObjectDefinition, GraphDefinition, *Attempt
  document/  — DocumentPath, DocumentName, DocumentNesting
  instance/  — ObjectInstance, GraphInstance
  location/  — ObjectLocation, AttributeLocation, ObjectReference
  obj/       — ObjectPath, ObjectName, ObjectNesting
  structure/
    notation/ — DocumentNotation, GraphNotation, ObjectNotation, AttributeNotation + cqrs/
    metadata/ — GraphMetadata, ObjectMetadata, TypeMetadata
    resource/ — ResourcePath, ResourceListing
objects/     — Bootstrap object definitions (Default*)
reflect/     — ClassMirror, ReflectionRegistry
service/
  context/   — GraphDefiner, GraphCreator
  media/     — NotationMedia (I/O)
  metadata/  — NotationMetadataReader
  notation/  — NotationReducer, NotationConventions
  parse/     — NotationParser
  store/     — LocalGraphStore, DirectGraphStore, RemoteGraphStore
util/        — collections, digests, misc
```

Platform code lives under `tech.kzen.lib.platform` (in jvmMain / jsMain): `ClassName`, persistent collections, datetime utilities. `commonMain` types depend only on `platform/`, not on the inverse.

## Critical files to read first

If you're new to kzen-lib, read these in order — they anchor every other concept:

1. `model/structure/GraphStructure.kt` — the unified entry point.
2. `model/structure/notation/GraphNotation.kt` — what a parsed document tree looks like.
3. `model/location/ObjectLocation.kt` — the address.
4. `model/definition/ObjectDefinition.kt` — typed analysis.
5. `model/instance/ObjectInstance.kt` — runtime.
6. `model/structure/notation/cqrs/NotationCommand.kt` (sealed hierarchy) — every mutation.
7. `service/store/LocalGraphStore.kt` + `service/store/DirectGraphStore.kt` — the store contract.
8. `service/notation/NotationReducer.kt` — where state changes actually happen.
