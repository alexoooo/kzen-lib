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
- `service/context/GraphCreator` — instantiates `GraphInstance`. `tryCreateGraph` returns a
  `GraphInstanceAttempt` (creation-side mirror of `GraphDefinitionAttempt`: instances plus a per-object
  `ObjectCreationFailure`); `createGraph` delegates to it and throws an aggregate when anything failed.

**Failures name their origin.** Broken notation degrades gracefully rather than aborting, so a failed
object is absent instead of loud — these are the two places that say why:

- `GraphDefinitionAttempt.failures` holds objects that failed to *define*;
  `GraphDefinitionAttempt.transitiveFailures` additionally covers objects that defined fine but were
  *pruned* from `transitiveSuccessful` (a dangling or required-but-empty reference, or a reference into
  another failed object — follow `missingObjects` for the root cause). It is a separate lazy;
  `transitiveSuccessful` stays the untouched hot path.
- `ObjectDefinitionFailure.attributeFailures` / `AttributeDefinitionFailure.unresolvedReference` carry
  the machine-readable cause (which attribute path, which reference, resolved against which host)
  alongside the display-oriented `attributeErrors`.

Hot-path caching along this flow (beyond the per-document parse cache and the dependency-digest-keyed
metadata cache):

- **Definition per notation version** — `DirectGraphStore` caches the `GraphDefinitionAttempt` keyed by
  the notation's content digest, so there is one `tryDefine` per notation version: repeat
  `graphDefinition()` calls (observe, refresh, the old-notation define inside a semantic command)
  return the same attempt instance, which also memoizes its lazy `transitiveSuccessful` pruning.
- **Coalesce survives edits** — `GraphNotation.with{New,Modified,out}Document` seed the successor's
  `coalesce` by patching the already-materialized one (per-document remove/put) instead of
  re-flattening every document; the inheritance-chain cache is deliberately not carried over.
- **Construction leveling is topological** — `GraphCreator.constructionLevels` resolves each declared
  reference once and peels zero-indegree levels (Kahn's algorithm, O(V+E)); an ambiguous reference is
  a clean `IllegalArgumentException` naming all candidates, and leftovers become per-object
  `ObjectCreationFailure`s rather than one aggregate throw. A failed lookup (`ObjectLocator.locate`)
  names same-name near misses — other documents, other nestings — instead of dumping every document path.
- **Closure content digest** — `GraphDefinition.transitiveDigest(documentPath | locations)` answers
  "did the transitive closure of X change?" as one `Digest`: an ordered combine (sorted by location
  string) over each closure member's location and coalesced `ObjectNotation` digest (memoized). It
  covers the notation the definitions were derived from, not the definitions themselves — definitions
  can embed definer-allocated runtime scaffolding and are never value-comparable across builds.
  Consumers (e.g. kzen-auto's live-edit migration baseline) compare digests instead of materializing
  and deep-comparing notation maps. The document-path form additionally digests the document's member
  list *in document order* and every object *notated* in the document, defined or not — a reorder, or
  an edit to a pruned-by-design member (a Job Worker with blank channel ports), must still invalidate
  validation caches and the live-edit migration signal.

**Runtime services (`@Service` injection).** A `@Reflect`'d class can declare constructor parameters
annotated `@Service` for values that can't be expressed in notation (stores, compilers, web-driver
holders); `GraphCreator` fills them from a host-supplied `GraphEnvironment` keyed by the parameter's
declared `ClassName`, so definitions stay environment-free and cacheable while only the create chain
carries the environment. Hosts assemble one with `GraphEnvironment.builder()`, registering services
either eagerly (`put(className, service)`) or — for composition-root cycles, where a registered
service is constructed after the environment itself — as a provider (`put(className) { service }`)
that resolves at most once, on first `resolve` at create time. A parameter declared as
`GraphEnvironment` resolves to the environment itself, so a graph object can re-enter the create
chain with the same services.

**Gotcha — typed-attribute YAML keys need a `meta:` declaration.** Writing a key like `name: "World"` into an object's notation does *not* by itself make `name` a typed attribute the Definition layer can wire into the constructor. The object's notation (or an ancestor in its `is:` chain) must also declare the type in a sibling `meta:` block, e.g. `meta: { name: String }`. Without it, `ObjectDefinition.attributeDefinitions` is empty and `AttributeObjectCreator` fails at construction with `Attribute definition missing: <document>#<object> - <attr>`. `NotationMetadataReader.inferMetadata` infers types for object-reference values but not for plain scalars — the explicit `meta:` is what tells the Definer how to coerce them. (Inference also skips a scalar whose target is `abstract: true`: an inferred hard reference to an abstract object could never define — abstract objects get no `ObjectDefinition` — so promotion would only get the host silently pruned by `transitiveSuccessful`; such scalars stay raw data. Attributes meant to *name* an object as data, rather than depend on it, should be meta-declared `by: Nominal` — see `WeakAttributeDefiner`.) Same rule applies when adding a new constructor parameter to a `@Reflect`'d class: bump the codegen *and* declare the attribute in `meta:` of the notation that constructs it.

**Both `meta:` and attribute values inherit most-derived-first — but by two different mechanisms, and only one of them merges.** `GraphNotation.inheritanceChain` linearizes the (possibly multiple-inheritance) ancestor graph C3-style, most-derived first, with shared ancestors and root sinking to the end. `firstAttribute` walks that chain and returns the first hit, so an attribute **value** declared on a subtype replaces the inherited one — and note what that implies for a list: a subtype declaring its own `uses:` *replaces* the base's rather than extending it, so a plugin author combining two mix-ins that each declare the same list attribute gets only one. `NotationMetadataReader.readObjectImpl` walks the same chain and keeps the first declaration of each attribute name, so an attribute's **`meta:` entry** replaces the inherited one the same way — wholesale, not key-by-key. A subtype adding one key (an `editor:`) must restate the rest (`is:` / `nullable:` / `by:`) or lose it. The one place merging *does* happen is a *type archetype's* `meta: ref:` map, which `readAttribute` overlays per key (`refMap.putAll(directMap)`): an attribute written `{is: SomeType, values: {…}}` overrides only `values` and still inherits that type's `by:` and `editor:`. *(Metadata was most-distant-**ancestor**-wins until 2026-08-02 — `readObjectImpl` overwrote unconditionally, so metadata inherited in the opposite direction from values and a subtype could not refine it at all. It looked benign for years because a restatement identical to its base is indistinguishable from being overwritten by it; only a subtype that genuinely **narrowed** an inherited attribute exposed it, as a definition failure in the subtype rather than an inheritance error.)*

**Gotcha — the inverse: a `meta:`-declared value scalar must resolve to a value.** Declaring a value-typed scalar in `meta:` (e.g. `group: String`, `icon: String`) makes every concrete object of that type require a value — its own, or an inherited default up the `is:` chain. There is no "optional/absent" handling for value scalars (`nullable` is honoured only for reference types): `StructuralAttributeDefiner.define` fails the attribute, the whole object fails to define, and it is **silently omitted** from `GraphDefinition.objectDefinitions` — the notation still loads fine, and the failure only surfaces later at runtime as `IllegalArgumentException: Missing: <doc>#main` from `filterTransitive`. To add an optional-looking display attribute, give the base type a sentinel default (`Boolean` → `false`, `String` → `""`) and normalize the sentinel in the reader (`?.asString()?.takeIf { it.isNotEmpty() }`).

## CQRS

All mutations to the notation layer go through commands and emit events. State is never edited in place.

```
NotationCommand   →   notationReducer.applyStructural()   →   NotationEvent   →   LocalGraphStore.Observer
   (intent)              (validation + apply)                   (fact)              (subscribers)
```

Key types in `model/structure/notation/cqrs/`:

- `NotationCommand` (sealed) — `StructuralNotationCommand` (create/rename/move/delete documents, folders, objects and attributes, plus the `*RefactorCommand`s that compose several of those into one event), `SemanticNotationCommand` (attribute value changes), and `ResourceNotationCommand` (add/remove a resource blob; itself a structural command).
- `NotationEvent` — immutable record of what changed; downstream consumers rebuild derived state from this stream.
- `NotationReducer` (`service/notation/NotationReducer.kt`) — the only place commands are applied; produces the event. A class (not a singleton `object`): it is constructed with a list of `CodeReferenceRewriter`s, so a refactor such as a rename can also emit downstream adjustments — e.g. kzen-auto rewriting the Kotlin expressions that reference a renamed step — bundled into the same event. The class is a thin dispatch facade holding only the `applyStructural`/`applySemantic` dispatchers; every command handler is a pure top-level function in a sibling file — the stateless per-command handlers split by target into `NotationReducer{Documents,Objects,Attributes,Resources}.kt`, the composite-attribute handlers (which compose lower-level commands through the top-level `StructuralBuffer`) in `NotationReducerComposite.kt`, and the semantic refactor + reference-analysis cluster in `NotationReducerRefactor.kt` (only the four dispatched entry points there are `internal`; the reference-analysis helpers stay file-private). Only `applySemantic` needs the instance — to thread `codeReferenceRewriters` into `renameObjectRefactor`; the structural dispatch and `StructuralBuffer` are instance-independent top-level symbols, which is what lets the composite/refactor handlers build a compound event from primitives without a reducer reference. The re-merge-inherited-value-before-local-edit invariant shared by the nested-attribute edits lives once in `remergeAttributeThenEdit`.

Why this matters:

- **Remote sync** — `RemoteGraphStore` ships events over the wire; the local and remote stores converge by replaying the same event log.
- **Auditing / undo** — the event log is a natural history.
- **Observation** — `LocalGraphStore.Observer` lets UI layers (kzen-auto-js, kzen-project-js) react to specific structural changes.

The reference implementation of the store is `service/store/DirectGraphStore` (in-process); `RemoteGraphStore` is the client-side proxy, and `MirroredGraphStore` composes the two so a browser applies each command locally for instant feedback *and* ships it to the server (see [`../../kzen-auto/docs/architecture.md`](../../kzen-auto/docs/architecture.md#2-client-server-graph-synchronization)).

**The YAML dialect is hand-rolled and non-standard.** Notation is parsed by kzen's own `util/yaml/YamlParser.kt` — not SnakeYAML/Jackson — so standard-YAML intuitions mislead. Escapes are processed in quoted scalars only: **double-quoted** scalars take JSON-style escapes (`\\`, `\"`, `\n`, `\t`, `\uXXXX`; an unknown escape throws), **single-quoted** scalars use standard `''` doubling plus a parse-only legacy backslash leniency (the emitter never produces backslashes in single quotes, so it is self-extinguishing), and **bare** scalars are never unescaped — but a bare value containing `:` (a Windows path, an icon name like `material-symbols:name`) mis-parses as a nested `key: value` map, so quote it. A Windows path must be double-quoted with doubled backslashes (`"C:\\Users\\me\\f.txt"`) — exactly what the emitter produces, so it round-trips: **doubled backslashes on disk are correct escaping, not a doubling bug**.

**Format-preserving deparse.** When a command persists a document, `YamlNotationParser.unparseDocument(notation, previousDocument)` honours the previous on-disk text as a template: it splits the previous document into per-top-level-object text segments and re-emits byte-identical the segment of every object whose parsed notation is unchanged, re-serializing only changed/added objects (and preserving leading document comments). So editing one object no longer strips comments/hand-formatting from the *other* objects in the same document (a resource-only change rewrites nothing). Accepted first-cut losses: comments *inside* a changed object are dropped, and inter-object blank-line runs normalize to a single blank line. A blank or unparseable `previousDocument` falls back to full serialization.

## Location-based identity

Every layer is indexed by **`ObjectLocation`** = `DocumentPath` + `ObjectPath`.

- `DocumentPath` — hierarchical document address (`model/document/`).
- `ObjectPath` — `ObjectName` + nesting within a document (`model/obj/`).
- `AttributeLocation` = `ObjectLocation` + `AttributePath` (`model/attribute/`, `model/location/`).

Cross-document references use `ObjectReference` + `ObjectReferenceHost`. The host scopes resolution so a partial reference can resolve to the most-local matching object — this is how documents pull in shared objects without fully-qualified addresses everywhere.

Reasoning about the codebase: when you see a function take an `ObjectLocation`, it works at *every* layer transparently because the three layers are aligned on that key.

### Document form — folders are first-class, and markerless

A `DocumentPath` denotes one of three on-disk shapes, made explicit as `DocumentForm` rather than a `directory: Boolean`, because a pure folder and a directory-document are distinct cases with distinct path encodings:

| Form | On disk | What it is |
|------|---------|-----------|
| `Document` | `<nesting>/<name>.yaml` | A regular notation file |
| `Directory` | `<nesting>/<name>/~main.yaml` | A directory document that owns a resource subtree |
| `Folder` | `<nesting>/<name>/` | A **pure directory** with no marker file, containing nested documents |

A `Folder` carries no notation of its own — it exists purely to organize. That makes it markerless, so the scanner must recurse into it and emit it even when empty. `NotationScan` (`model/structure/scan/`) is the on-disk tree, keyed by `DocumentPath`, so folder entries appear as keys with **`DocumentPath.folder == true`** — any consumer iterating a scan expecting real documents must filter on that. Folders are manipulated through their own commands: `CreateFolderCommand` / `DeleteFolderCommand`, and the refactors `RenameFolderRefactorCommand` / `MoveFolderRefactorCommand`, which do re-nest the contained documents (`RenameFolderRefactorTest`).

> **Gotcha — the *document* refactor does not cascade to a directory's children.** `RenameDocumentRefactorCommand` copies only the marker document, so pointing it at a non-empty folder orphans the contents. Use the folder refactors above.

### Position — document order is itself addressable

Object order within a document is meaningful, not incidental, so it is part of the notation model rather than an explicit ordering list. `PositionIndex` is an absolute slot; `PositionRelation` (`relativeIndex` + `At` / `After`, with `first` / `last` / `afterLast` helpers) expresses a target position relative to the current contents, which is what an insert or a drag needs. `PositionedObjectLocation` / `PositionedObjectPath` / `PositionedAttributeNesting` pair a location with its slot.

`ShiftObjectCommand` / `ShiftObjectTreeCommand` reorder within a document (the tree variant carries an object's nested children with it); `RelocateObjectTreeRefactorCommand` moves a subtree across documents as a refactor.

The consumer-facing half is **`NestedListAttributeDefiner`** (`objects/general/`): it auto-wires the objects nested directly under a given attribute *in document order* into a `List<ObjectLocation>` constructor parameter. So a parent references its children by structure rather than by an explicit list of references — which is how kzen-auto's Script steps derive their execution order from their position in the document. The emitted references are **weak**, so they materialize as locations and impose no construction ordering.

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

**Definer vs Creator — the phase split governs when you can resolve an instance.** Definition and creation are two separate passes (see [Document load flow](#document-load-flow)), and the `partialGraphInstance` handed to each SPI differs accordingly. An `AttributeDefiner` (and `ObjectDefiner`) runs in the *definition* pass, where that partial graph holds **only** the bootstrap definer/creator objects — never user objects, which haven't been constructed yet. So a definer can read notation + metadata and emit an `AttributeDefinition`, but it **cannot resolve another object's instance**. To inject a sibling object's instance — or a view derived from it — into a constructor parameter, do it in an `AttributeCreator`, which runs in the *creation* pass where `partialGraphInstance` holds every already-constructed object. Select a custom creator per attribute with the **`creator:`** notation key — the creation-pass parallel to **`by:`**, which selects a custom definer. Construction order is dependency-driven: a *strong* `ReferenceAttributeDefinition` (what the default `StructuralAttributeDefiner` emits for a non-primitive scalar value) forces the referenced object to be built first, so a creator can resolve it; *weak* references (`WeakAttributeDefiner`, `NestedListAttributeDefiner`) materialize as `ObjectLocation`s and impose no ordering. (Concretely in kzen-auto: Flow allocates fresh per-vertex channel holders in a *definer* because nothing shared is resolved, whereas Job wires each worker to a *shared* channel instance in a *creator* — for exactly this reason.) The same phase rule bans autowired instance-list constructor parameters (`is: List / of: X / by: Autowired`) on an `AttributeDefiner` object: `GraphDefiner` instantiates definers mid-definition awaiting only `creatorDependencies`, so the autowired instances don't exist yet and creation throws `Missing: <definer> - <referenced object>`, killing the whole definition pass — put per-type dispatch *data* in notation attributes the definer reads from `graphStructure`, and per-type *code* in classes autowired into the creator. One more wiring note: a class whose constructor takes its own `objectLocation` gets it via the `Self` definer — `meta: { objectLocation: { is: ObjectLocation, by: Self } }`; without the declaration the graph fails at instantiation (on the client, at boot).

**Class instantiation — `GlobalMirror` and the JVM reflective fallback.** A notation `class:` FQN (nested classes `$`-joined, i.e. the JVM binary name) is resolved through `GlobalMirror`, a delegate chain consulted in registration order. `ReflectionRegistry.global` — which the KSP-generated `*Module.register()` calls populate — is seeded first and always wins; hosts append further delegates with `GlobalMirror.register(...)`, so a fallback only ever sees classes codegen missed.

On the JVM that fallback is `ReflectiveClassMirror` (kzen-lib-jvm `server/reflect/`), which serves classes annotated `@Reflect` that have no generated registration: kotlin-reflect primary-constructor introspection, `@Service` parameters detected at runtime, Kotlin `object`s served as their singleton, and Java classes supported when compiled with `-parameters` (KSP registers Kotlin sources only). It logs every class it serves, because JS has no runtime net — codegen is mandatory there, so a log line marks a class the JS client could not instantiate. `KzenAutoContext` and the test bootstraps register it; per-classloader instances are the seam for plugin loaders.

## Execution model (Logic / Task / Trace)

`exec/` holds general execution abstractions — not kzen-auto domain concepts. They relocated here from kzen-auto: the `Logic`/`Task` types were always platform-agnostic, and `Logic` is the abstraction that consumes `ObjectStableMapper`, so the two belong in the same module.

> For the **implementation-agnostic functional requirements** of the Logic framework, see
> [`logic-spec.md`](logic-spec.md) — a *living* specification. It was written when the implementation had
> grown sprawling, to state precisely what must hold as the basis for a simpler design; **that design has
> since been built**, and the spec now leads the implementation rather than describing a target. The
> section below is the vocabulary and the package layout only — every mechanism named here (quiescence,
> stepping, migration, resources, the trace contract) is specified in full there, and that is the one
> place to change when behaviour changes.

| Concept | What it is | Key types |
|---------|-----------|-----------|
| **Logic** | The unit of interactive computation: a suspendable `run(execution): TupleValue`. Position lives on the coroutine stack — a sequence is statements, a loop is a `for` — so there is no re-entrant "continue-or-start" and no manual position persistence. Outcomes are return / throw / cooperative cancel; *paused* is not a return value but a suspension. | `Logic`, `LogicSignature`, `LogicFailure`, `Repositionable` |
| **Execution** | The entire surface a Logic touches, handed to it by the engine. Boundaries (`checkpoint`), tracing (`emit` / `log` / `resetEmitted`), composition (`host`, which also bootstraps the child frame with call-site-supplied bindings), ambient context and disposal (`declareExport(selector)` / `bind` / `binding` / `hasBinding` / `hasBindingInFamily` / `releaseBinding` / `onSettle`, over a supported raw-string interop layer of `resource` / `resourceValue` / `releaseResource`), interaction (`onRequest`), and live-edit state (`onCapture` / `restored` / `moveTarget` / `moveDescendCallSite`). | `Execution`, `Address`, `PauseReason`, `ContextKey`, `ExportSelector`, `FrameDisposal`, `InitialBinding`, `ClosePolicy` |
| **Engine** | The single-writer core that owns everything mutable for one run: the node tree, the append-only event log, each node's live per-address value map, identity, resources, and the live-edit migration barrier. One per run — no singleton. Quiescence (the coherent wavefront that pause / step / edit act on) is counted, not guessed. | `RunEngine`, `CountingDispatcher`, `Run`, `RunState`, `Node`, `NodeStatus`, `Outcome`, `StepMode`, `TraceEvent` |
| **Trace** | The values a run records — latest-per-address plus an append-only history. A **projection of the retained engine**, not a store. | `LogicTrace`, `LogicTracePath`, `LogicTraceQuery`, `LogicTraceSnapshot`, `LogicTraceEntry`, `LogicTraceEvent`; run models `LogicRunId` / `LogicExecutionId` / `LogicStatus` / `LogicRunState` |
| **Task** | A one-shot async unit of work tracked to completion. Unrelated to Logic — no pause, step, or trace. | `ManagedTask`, `TaskHandle`, `TaskRepository`, `TaskModel`, `TaskState` |
| **Tuple** | The named-component value/definition model for Logic inputs and outputs. | `TupleDefinition`, `TupleValue`, `TupleComponent*` |

Interfaces and pure-data models live in `kzen-lib-common/commonMain` (`exec/engine/` for the core, `exec/logic/` for the run-control and trace *wire* models). Server-side execution is `kzen-lib-jvm` `server/exec/engine/` — `RunEngine` + `CountingDispatcher`, the only two JVM-side files. **There is no separate trace store** — the former `LogicTraceStore` was retired; the `LogicTrace` wire contract is served by projecting the retained engine at query time (kzen-auto's `RunEngineLogicTrace`, see [`../../kzen-auto/docs/architecture.md`](../../kzen-auto/docs/architecture.md) §3).

**Run vs execution — nested traces live in separate nodes, merged per run.** A `LogicRunId` identifies one top-level run; a `LogicExecutionId` identifies one execution of a logic *within* that run — 1:1 with an engine node id. When a logic *hosts another logic* — kzen-auto's `RunStep` running a linked sub-script via `Execution.host` — the sub-logic is a **new node under the same run**, so its live values land in a **separate node**: a single `lookup(parentRunExecutionId, …)` does *not* include them (it reads exactly one node's live map, translated to wire paths). To read a whole run at once, `lookupRun(logicRunId, …)` merges **every node of the run** into one snapshot, keeping only the latest node per stable id and resolving residual duplicate paths by the highest sequence (each live entry carries its `TraceEvent.sequence` via `Node.liveSequence`) — reproducing the former store's re-entry clearing. kzen-auto's client `ScriptProgressStore` does exactly this — one `mostRecent(scriptRoot)` to discover the run, then one `lookupRun` — and derives each `RunStep`'s execution-ordered screenshots from the run's history (log events, via `lookupRunHistory`). Individual executions stay separately addressable via `lookup`; `lookupRun` is the run-wide read.

**Stepping across concurrent spines.** `RunEngine` computes Step Over / Step Out centrally from tree depth, and two choices are load-bearing precisely because they only misbehave under a *multi-spine* run (Job): the step `limit` is the **shallowest** parked frame (`minOf`, never `maxOf` — a concurrent Job parks siblings at different depths, and referencing an already-descended child's depth re-descends Step Over into it), and `SteppingOver` / `SteppingOut` stay active for the whole step rather than collapsing to `Paused` when one spine parks at its boundary (collapsing lets a shallow worker catch a deeper still-running child at its next checkpoint and park it inside). A single-spine Script/Flow parks exactly one node at a time (min depth == max depth), so single-spine tests cannot catch regressions here — any change to Step Into/Over/Out or `checkpoint` depth logic must be validated with a concurrent-spine test (`RunEngineTest.stepOverRunsAlreadyDescendedConcurrentChildFree` is the pin).

Nothing in the engine limits a process to one run — an engine is created per run and owns only that run's state. The **server** currently tracks a single active run (kzen-auto's `ServerLogicController`), retaining it after it settles so its trace stays readable for post-run review; lifting that is a known deferred item (`logic-spec.md` §2).

**Two write modes — resettable live view vs append-only history.** A Logic writes only through its `Execution`, and the choice is one flag.

- `emit(address, value)` records the **current** value at an address (live latest-value-per-address). It is what a step / vertex / worker display reads, and `resetEmitted(addresses, callSites)` clears it — that is how a loop (`ForEachStep`, `DoWhileStep`) presents a fresh trace each iteration instead of the previous iteration's finished one. By default the write is **also** appended to the run's history.
- `emit(address, value, retain = false)` makes the write **transient**: live view and observers only, no history entry. This is the bound on a high-churn progress signal (a throttled row count, a "running" marker) that would otherwise grow history without limit. Script's step traces use it.
- `log(value)` appends an immutable event to the run's history timeline and nothing else. History survives every reset, so it is the **film strip** of a whole run — and it is **value-agnostic**: kzen-auto logs browser screenshots as `BinaryExecutionValue`, but any Logic can log any `ExecutionValue`.

Read history with `lookupRunHistory(runId, sinceSequence)` — run-wide, ordered by sequence, returning only events past the watermark so a client polls incrementally without re-sending bytes. kzen-auto's RunStep detail film strip is built from the binary-valued events of a RunStep's whole subtree.

`LogicTraceHandle` (`set` / `append` / `clearAll` / `register`) is **not** this mechanism — it is the legacy write-side interface kept for one adapter, kzen-auto's `ExecutionLogicTraceHandle`, which maps the Report pipeline's literal trace paths onto `Execution.emit`. Its `register` and `clearAll` are no-ops there. New code writes through `Execution`.

**Concrete wiring stays in the consumer (kzen-auto):** `ServerLogicController` (the run state machine, live-edit detection, and the REST-facing verbs the engine deliberately doesn't know about), `ModelTaskRepository`, and `LogicConventions` (the REST wire surface) are HTTP / thread-pool concerns that don't belong in lib. So is the *flavour* seam: a runnable document's `main` archetype implements kzen-auto's `LogicDocument` and compiles itself to a `Logic` — `ScriptDocument` → `ScriptLogic` is the reference case — see [`../../kzen-auto/docs/architecture.md`](../../kzen-auto/docs/architecture.md) § 1.

## Package map

Top-level `tech.kzen.lib.common`:

```
api/         — SPI: ObjectDefiner, ObjectCreator, AttributeDefiner/Creator
exec/        — execution-layer abstractions (Logic/Task/Trace; relocated from kzen-auto)
  engine/    — the Logic core: Logic, Execution, Run, RunState, Node, NodeId, NodeStatus, Outcome,
               OutcomeTrace, PauseReason, Address, TraceEvent, TraceReset, LogicSignature,
               LogicFailure, Repositionable, ClosePolicy, StepMode
    context/ — ambient-binding addressing: ContextKey, ContextFamily, ExportSelector,
               BindingLookup, RetainedBinding, InitialBinding (a call site's child bootstrap)
    disposal/— FrameDisposal (one-shot, at-most-once), SettleDisposalPolicy
  logic/     — the run-control + trace WIRE models (not the core):
               run/ (LogicController; model/ LogicRunId/ExecutionId/RunExecutionId, LogicStatus,
                     LogicRunState/Info/Response, LogicRunExecutionInfo, LogicRunFrameInfo);
               trace/ (LogicTrace, LogicTraceHandle, model/LogicTracePath/Query/Snapshot/Entry/Event);
               model/ (LogicDefinition, LogicType); ResourceClosePolicy
  task/      — ManagedTask, TaskHandle, TaskRepository, TaskRun; model/ (TaskId/Model/Progress/State)
  tuple/     — TupleDefinition/Value, TupleComponentDefinition/Name/Value
  (root)     — ExecutionRequest, ExecutionResult, ExecutionValue, RequestParams
model/
  attribute/ — AttributeName, AttributePath, AttributeNesting, AttributeSegment, AttributeNameMap
  definition/ — ObjectDefinition, GraphDefinition, ObjectDefinitionReference, *Attempt/*Failure
  document/  — DocumentPath, DocumentName, DocumentNesting, DocumentSegment, DocumentForm
  instance/  — ObjectInstance, GraphInstance, GraphInstanceAttempt, ObjectCreationFailure
  location/  — ObjectLocation, AttributeLocation, ResourceLocation, ObjectReference(+Host/Name),
               ObjectLocator, LocateErrors, ObjectLocation{Map,Set}
  obj/       — ObjectPath, ObjectName, ObjectNesting, ObjectNestingSegment, ObjectPathMap
  structure/
    notation/ — DocumentNotation, DocumentObjectNotation, GraphNotation, ObjectNotation,
                AttributeNotation, PositionIndex/Relation, Positioned* + cqrs/ + codec/
    metadata/ — GraphMetadata, ObjectMetadata, TypeMetadata, AttributeMetadata + tag/
    resource/ — ResourcePath, ResourceListing, ResourceContent/Directory/Info/Name/Nesting
    scan/     — NotationScan, DocumentScan (the on-disk document tree, folders included)
objects/     — bootstrap + built-in SPI implementations
  base/      — AttributeObjectDefiner/Creator, StructuralAttributeDefiner, ServiceAttributeCreator
  bootstrap/ — DefaultConstructorObjectDefiner/Creator, BootstrapConventions
  general/   — Autowired/Weak/Self/Codec/ParentChild/NestedList AttributeDefiner
reflect/     — @Reflect/@Service, ClassMirror, GlobalMirror, ReflectionRegistry, ModuleReflection
service/
  context/   — GraphDefiner, GraphCreator; environment/ (GraphEnvironment + builder — @Service DI)
  media/     — NotationMedia (I/O) + ReadWrite/Literal/Map/Seeded impls
  metadata/  — NotationMetadataReader, MirrorMetadataReader
  notation/  — NotationReducer (+ per-target handler files), NotationConventions, CodeReferenceRewriter
  parse/     — NotationParser, YamlNotationParser
  store/     — LocalGraphStore, DirectGraphStore, RemoteGraphStore, MirroredGraphStore;
               normal/ (ObjectStableMapper, ObjectStableId)
util/        — digest/ (Digest, DigestCache, Digestible), naming/, yaml/, ImmutableByteArray
```

Platform code lives under `tech.kzen.lib.platform` — declared in `commonMain` and implemented per target in jvmMain / jsMain: `ClassName`, `collect/` (persistent collections), `DateTimeUtils`, `IoUtils`, `PlatformSynchronized`. `commonMain` types depend only on `platform/`, not on the inverse.

Beyond `kzen-lib-common` the repo holds `kzen-lib-jvm` (JVM-only server code: `server/exec/engine/` for `RunEngine` + `CountingDispatcher`, `server/notation/` for file-backed media, `server/reflect/` for the reflective mirror, `server/codegen/`), `kzen-lib-js`, and `kzen-lib-reflect-ksp` (the KSP processor that generates each module's `ModuleReflection`).

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
