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

**Gotcha — typed-attribute YAML keys need a `meta:` declaration.** Writing a key like `name: "World"` into an object's notation does *not* by itself make `name` a typed attribute the Definition layer can wire into the constructor. The object's notation (or an ancestor in its `is:` chain) must also declare the type in a sibling `meta:` block, e.g. `meta: { name: String }`. Without it, `ObjectDefinition.attributeDefinitions` is empty and `AttributeObjectCreator` fails at construction with `Attribute definition missing: <document>#<object> - <attr>`. `NotationMetadataReader.inferMetadata` infers types for object-reference values but not for plain scalars — the explicit `meta:` is what tells the Definer how to coerce them. Same rule applies when adding a new constructor parameter to a `@Reflect`'d class: bump the codegen *and* declare the attribute in `meta:` of the notation that constructs it.

## CQRS

All mutations to the notation layer go through commands and emit events. State is never edited in place.

```
NotationCommand   →   notationReducer.applyStructural()   →   NotationEvent   →   LocalGraphStore.Observer
   (intent)              (validation + apply)                   (fact)              (subscribers)
```

Key types in `model/structure/notation/cqrs/`:

- `NotationCommand` (sealed) — `StructuralNotationCommand` (create/rename/delete documents, objects, attributes) and `SemanticNotationCommand` (attribute value changes).
- `NotationEvent` — immutable record of what changed; downstream consumers rebuild derived state from this stream.
- `NotationReducer` (`service/notation/NotationReducer.kt`) — the only place commands are applied; produces the event. A class (not a singleton `object`): it is constructed with a list of `CodeReferenceRewriter`s, so a refactor such as a rename can also emit downstream adjustments — e.g. kzen-auto rewriting the Kotlin expressions that reference a renamed step — bundled into the same event.

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

## Stable identity (`ObjectStableMapper`)

`ObjectLocation` addresses *current* state, but it is **rename-mutable** — renaming a document or object changes the location of everything under it. Long-lived execution state (a `LogicTrace`, a paused run's per-step models) must outlive renames, so it is keyed by **`ObjectStableId`** — a stable token minted on first encounter (`ObjectStableId(objectLocation.asString())`) that never changes afterward.

`ObjectStableMapper` (`service/store/normal/`) is a `LocalGraphStore.Observer` that maintains a bidirectional `ObjectLocation ↔ ObjectStableId` map. It updates the map in place on the relevant CQRS events — `RenamedObjectEvent` / `RenamedNestedObjectEvent` / `RenamedDocumentRefactorEvent` re-point an existing id to the new location; `RemovedObjectEvent` / `DeletedDocumentEvent` drop it. `objectStableId(location)` is lookup-or-mint; `objectLocation(id)` translates back; `snapshot()` / `seed(...)` move the whole map across the wire.

There is **one mapper per process**, observing the local graph store from boot:

- **Server** — constructed in `KzenAutoContext`, `graphStore.observe(...)` once (never unobserved), and *pre-warmed* by iterating the initial notation so ids deterministically reflect names-at-boot. This is what lets a trace survive a rename even in the gap between a run ending and the user editing the notation afterward.
- **Client** — constructed in `ClientContext`, `seed()`ed from the server's `snapshot()` at connect, then observing `mirroredGraphStore`. The client can therefore translate a stable-keyed trace path back to the current `ObjectLocation` locally, without re-fetching from the server on every notation edit.

> Accepted limitation: the mapper assumes a single linear history of notation changes. Revert / version-control would require restarting execution.

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

**Definer vs Creator — the phase split governs when you can resolve an instance.** Definition and creation are two separate passes (see [Document load flow](#document-load-flow)), and the `partialGraphInstance` handed to each SPI differs accordingly. An `AttributeDefiner` (and `ObjectDefiner`) runs in the *definition* pass, where that partial graph holds **only** the bootstrap definer/creator objects — never user objects, which haven't been constructed yet. So a definer can read notation + metadata and emit an `AttributeDefinition`, but it **cannot resolve another object's instance**. To inject a sibling object's instance — or a view derived from it — into a constructor parameter, do it in an `AttributeCreator`, which runs in the *creation* pass where `partialGraphInstance` holds every already-constructed object. Select a custom creator per attribute with the **`creator:`** notation key — the creation-pass parallel to **`by:`**, which selects a custom definer. Construction order is dependency-driven: a *strong* `ReferenceAttributeDefinition` (what the default `StructuralAttributeDefiner` emits for a non-primitive scalar value) forces the referenced object to be built first, so a creator can resolve it; *weak* references (`WeakAttributeDefiner`, `NestedListAttributeDefiner`) materialize as `ObjectLocation`s and impose no ordering. (Concretely in kzen-auto: Flow allocates fresh per-vertex channel holders in a *definer* because nothing shared is resolved, whereas Job wires each worker to a *shared* channel instance in a *creator* — for exactly this reason.)

## Execution model (Logic / Task / Trace)

`exec/` holds general execution abstractions — not kzen-auto domain concepts. They relocated here from kzen-auto on 2026-05-28: the `Logic`/`Task` types were always platform-agnostic, and `Logic` is the abstraction that consumes `ObjectStableMapper`, so the two belong in the same module.

> For the **implementation-agnostic functional requirements** of the Logic framework (the basis for
> re-architecting it), see [`logic-spec.md`](logic-spec.md). The section below describes the *current*
> wiring; the spec describes *what must hold* — and deliberately diverges where today's design relies on
> global singletons or run-global resources.

| Concept | What it is | Key types |
|---------|-----------|-----------|
| **Logic** | A long-running, stateful execution that can be paused, stepped, and resumed, emitting a trace as it goes. | `Logic`, `LogicHandle`, `LogicControl`, `LogicExecution`, `LogicDefinition` (tuple in/out), `LogicResult` |
| **Trace** | The timestamped values a Logic run records — per-path snapshots plus an append-only event history. | `LogicTrace`, `LogicTraceHandle`, `LogicTracePath`, `LogicTraceQuery`, `LogicTraceSnapshot`, `LogicTraceEntry`, `LogicTraceEvent` |
| **Task** | A one-shot async unit of work tracked to completion. | `ManagedTask`, `TaskHandle`, `TaskRepository`, `TaskModel`, `TaskState` |
| **Tuple** | The named-component value/definition model for Logic inputs and outputs. | `TupleDefinition`, `TupleValue`, `TupleComponent*` |

Interfaces and pure-data models live in `kzen-lib-common/commonMain`. Server-side execution *primitives* live in `kzen-lib-jvm`: `server/exec/logic/context/` (`LogicContext`, `LogicFrame`, `MutableLogicControl`) and `server/exec/logic/trace/LogicTraceStore` — the in-memory, `ObjectStableId`-keyed trace store (see [Stable identity](#stable-identity-objectstablemapper)).

**Run vs execution — nested traces live in separate buffers, merged per run.** A `LogicRunId` identifies one top-level run (at most one active at a time); a `LogicExecutionId` identifies one execution of a logic *within* that run (the initial execution shares the run id's value). `LogicTraceStore` keys each trace buffer by the full `LogicRunExecutionId`, and stamps every write (`LogicTraceHandle.set`, the single choke point for all trace writes) with a wall-clock `Instant` and a process-global monotonic sequence — that pair is a `LogicTraceEntry`. When a logic *starts another logic* — kzen-auto's `RunStep` running a linked sub-script via `LogicHandleFacade.start` — the sub-logic is allocated a **new `LogicExecutionId` under the same `LogicRunId`**, so its traces land in a **separate buffer**: a single `lookup(parentRunExecutionId, …)` does *not* include them. To read a whole run at once, `lookupRun(logicRunId, …)` merges **every buffer sharing that run id** (the main script plus all nested sub-script executions) into one snapshot, resolving duplicate paths — a sub-script invoked more than once — by the latest write (highest sequence). kzen-auto's client `ScriptProgressStore` does exactly this — one `mostRecent(scriptRoot)` to discover the active run id, then one `lookupRun` — and derives each `RunStep`'s execution-ordered screenshots from the merged snapshot (sorted by entry sequence). The buffers stay separate (so an individual execution is still addressable via `lookup`); `lookupRun` is the run-wide read.

**Two write modes — latest-per-path vs append-only history.** `LogicTraceHandle.set(path, value)` keeps the *current* value per path (live state); it is wiped by `clearAll` — which is how a loop (`MappingStep`) resets its body between iterations, so only the last iteration's per-path values survive. `LogicTraceHandle.append(objectStableId, value)` instead records an immutable `LogicTraceEvent` on the run's history timeline, which `clearAll` never touches — so every iteration's and every nested execution's events are retained. The timeline is **value-agnostic**: any Logic can append any `ExecutionValue` (kzen-auto appends browser screenshots as `BinaryExecutionValue`; a `CustomLogic` could append its own). Read it with `lookupRunHistory(runId, sinceSequence)` — run-wide, ordered by sequence, returning only events past the watermark so a client polls incrementally without re-sending bytes. kzen-auto's RunStep detail film strip is built from the binary-valued events of a RunStep's whole subtree.

**Concrete wiring stays in the consumer (kzen-auto):** `ServerLogicController` (the run state machine), `ModelTaskRepository`, and `LogicConventions` (the REST wire surface) are HTTP / thread-pool concerns that don't belong in lib. kzen-auto's `ScriptDocument` is the reference `Logic` implementation — see [`../../kzen-auto/docs/architecture.md`](../../kzen-auto/docs/architecture.md) § 1.

## Package map

Top-level `tech.kzen.lib.common`:

```
api/         — SPI: ObjectDefiner, ObjectCreator, AttributeDefiner/Creator
codegen/     — code generation helpers
exec/        — execution-layer abstractions (Logic/Task/Trace; relocated from kzen-auto 2026-05-28)
  logic/     — Logic, LogicHandle/Control/Execution (+ *Facade), StatefulLogicElement;
               run/model/ (LogicRunId/ExecutionId/RunExecutionId, LogicStatus, LogicRun*);
               trace/ (LogicTrace, LogicTraceHandle, model/LogicTracePath/Query/Snapshot/Entry/Event);
               model/ (LogicCommand/Definition/Result/Type)
  task/      — ManagedTask, TaskHandle, TaskRepository, TaskRun; model/ (TaskId/Model/Progress/State)
  tuple/     — TupleDefinition/Value, TupleComponentDefinition/Name/Value
  (root)     — ExecutionRequest, ExecutionResult, ExecutionValue, RequestParams
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
  notation/  — NotationReducer, NotationConventions, CodeReferenceRewriter (refactor hook)
  parse/     — NotationParser
  store/     — LocalGraphStore, DirectGraphStore, RemoteGraphStore; normal/ObjectStableMapper
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
