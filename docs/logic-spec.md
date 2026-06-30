# Logic — functional specification

> **Status: living specification.** This document defines the *functional requirements* of the kzen Logic
> execution framework **independently of how it is currently implemented**. It exists because the current
> implementation has grown complex and sprawling (split across `kzen-lib` and `kzen-auto`), and we want a
> precise, implementation-agnostic statement of *what must hold* as the basis for brainstorming a
> dramatically simpler design. Requirements are stated in §1–§7; §8 analyses where they interact to force
> today's complexity; the appendix maps each requirement to the current build so a reader can see what
> exists. Where §1–§7 and the current code disagree, **the requirement wins** — those gaps are deliberate
> targets.

## What a Logic is

A **Logic** is kzen's abstraction for **general-purpose interactive computation**: a typed, stateful
computation that can be **run, paused, stepped, resumed, and cancelled**; that is **observable** (it emits a
trace as an intrinsic part of running); that can be **edited while paused and resumed against the new
definition**; and that **composes** — a Logic can host other Logics as confined children.

The model is **agnostic to any particular flavour of logic.** kzen-auto's three paradigms — **Script**
(sequential steps), **Flow** (synchronous vertex DAG), and **Job** (concurrent workers over channels) — are
**example consumers**, not part of the model. The requirements below are the *generalization* of what those
three need; the design must support all of them **without constraining future implementations** of
interactive computation (a debugger, a notebook, a state machine, a long-running daemon, …).

**Observability and live-edit/migration are part of the core model, not consumer concerns.** Tracing is not
a logging facility bolted onto a finished computation — it is the mechanism by which interactive computation
is *watched and steered*, so it belongs in the same core (`kzen-lib`) as execution and control. Likewise,
the ability to edit a paused computation and migrate its in-flight state is a property of the model itself;
the core must provide it use-case-agnostically, so that *any* Logic implementation inherits it rather than
re-inventing it.

---

## 1. Scope

**In scope** (the core model, `kzen-lib`):
- Execution model — the tree of executions, concurrency, quiescence, heterogeneous composition (§2).
- Typed inputs and outputs (§3).
- Run control — run / pause / cancel / step, boundaries, pause reasons, outcomes, auto-run, interactive
  request/response (§4).
- Live edit while paused **and** state migration (§5) — **core, use-case-agnostic**.
- Resource lifecycle, scoped within the execution tree (§6).
- Observability / tracing, intrinsic to the model (§7).

**Out of scope:**
- The **Task** subsystem (`ManagedTask` / `TaskRepository`) — a separate one-shot async abstraction with
  its own (future) spec.
- The notation → definition → instance graph layers and CQRS — see [`architecture.md`](architecture.md);
  this spec treats "the definition" as a given, editable, addressable description.
- The concrete paradigms (Script / Flow / Job) except as **illustrative consumers**.

---

## 2. Execution model

- **Host:** single OS process, JVM backend, Kotlin (Java permitted where it helps). **Not distributed** —
  no cross-process or cross-machine execution is required.

- **Execution tree (not a linear stack).** A top-level execution spawns sub-executions, forming a **tree**.
  Under structured concurrency **multiple leaf frames can be active at the same time**, so the runtime state
  is a tree with possibly-many concurrently-running leaves — not a single call stack. Each node is one
  *execution* of some Logic; the same Logic definition may appear as many nodes (a loop body, a
  sub-computation invoked repeatedly), and each node is independently addressable.

- **Concurrency is opt-in, cooperative, and per-flavour.**
  - The default is **synchronous, single-threaded** execution (a sequential paradigm runs to its next
    boundary on one thread).
  - A Logic **may** introduce **structured concurrency** — spawning parallel sub-executions that run
    genuinely in parallel. The model must support this, but must not *require* every Logic to be concurrent.
  - All parallel work cooperates through **safe-points (checkpoints)**: the points at which a worker is
    willing to be paused, stepped, or cancelled. Blocking work must remain visible to the runtime so it can
    tell "busy" from "idle".

- **Quiescence (the real requirement behind "pause/step must work across the whole parallel execution").**
  The runtime must be able to bring all parallel work to a **consistent, settled state** at a boundary — a
  *quiescent wavefront* where every concurrent spine is parked at a safe-point — so that pause, step, and
  edit act coherently rather than racing live mutation. **Cancellation is cooperative**, observed at
  safe-points, not a hard kill.

- **Heterogeneous composition.** Any Logic may invoke **any other Logic** as a child — regardless of
  flavour (a Script may host a Job; a Job worker may host a Script; a Logic may host another instance of
  itself). Each child is **confined**: it runs with its **own control, its own trace scope, and its own
  resource scope**, sharing only immutable inputs and stateless services with its parent and siblings. This
  is what makes concurrent children safe and is the only composition primitive the model needs — every
  "run a sub-computation" feature (Script's run-step, Flow's run-vertex, Job's run-worker) is the same
  underlying capability.

- **No global singletons (explicit anti-goal).** The model must **not** depend on process-global state.
  Concretely: there must be no requirement that *at most one run is active*, no single shared run
  controller, no single shared trace store, no single shared identity map, no run-global "clear everything".
  All run state must be **per-run / per-execution-tree**, so that:
  - **multiple top-level runs can execute concurrently**, fully isolated from one another, and
  - **non-interactive background runs** (no attached UI, no stepping) are possible.

  > Today exactly one run is active at a time and several stores are process singletons. That is a current
  > limitation, not a requirement — the spec requires the opposite.

---

## 3. Typed inputs and outputs

- A Logic declares a **typed signature**: inputs and outputs are each a **named, typed tuple** (not
  positional parameters).
- An invocation accepts **zero or more** input components and produces:
  - **void** (zero output components),
  - a single **`main`** output component (the common case), or
  - **several named** output components.
- A conventional **`detail`** component carries auxiliary/observational output (e.g. a rich value for the
  UI) distinct from the primary `main` result.
- Types are **first-class** (carried as type metadata) and used to **validate wiring before running** where
  the flavour allows it (definition-time errors surfaced to the user), rather than failing as a runtime
  cast. The model must permit **user-defined and dynamically-discovered** component types.

---

## 4. Run control

Run control is expressed against a **specific run** (identified per-run, never "the" run), so every command
below is addressed and concurrent runs are independently controllable.

- **Run** — execute at full speed to the next halt (terminal, or a pause).
- **Pause** — settle at the next boundary into a quiescent state.
- **Cancel / terminate** — cooperatively stop the run; it settles to a terminal *cancelled* outcome,
  releasing resources.
- **Step** — advance by exactly **one boundary**, in three modes:
  - **Step into** — descend into a child execution if the next boundary enters one.
  - **Step over** — run any child entered on this boundary **to completion**, pausing at the current
    frame's next boundary.
  - **Step out** — run the current frame (and its descendants) to completion, pausing at the **caller's**
    next boundary; at the root this runs the whole Logic to its end.

  Step semantics must hold **across flavour boundaries** — stepping over / out of a heterogeneous child
  (e.g. a Job hosted inside a Script) must behave as if it were the same flavour.

- **What a "boundary" is.** Each Logic **defines its own unit of progress** — its step/checkpoint
  granularity. A boundary is a point where the computation is in a coherent, observable, pausable state
  (a Script's step, a Flow's vertex, a Job's batch wavefront). Stepping advances one such boundary; the
  unit is the flavour's choice, and the model must not hard-code a single notion of "a step".

- **Pause-on-error** — a per-run option, **live-togglable while paused**, taking effect at the next
  boundary: when on, a recoverable failure **pauses** the run for fix-and-resume instead of ending it.

- **Distinct pause reasons.** A pause carries *why* it happened, and the reason **propagates upward
  unchanged** through nested logic (a pause deep inside a child surfaces with its real reason):
  - **boundary** — the ordinary step-settle (an auto-step loop keeps going),
  - **explicit** — the computation paused *itself* (a breakpoint / pause-step — a deliberate halt),
  - **error** — a failure paused under pause-on-error (a deliberate halt).

  The distinction is functional: interactive clients treat *boundary* (keep advancing) differently from
  *explicit* / *error* (stop and wait for the user).

- **Internal (self-)pause.** A Logic can pause itself by resolving a boundary as *paused (explicit)* rather
  than continuing — the mechanism by which a breakpoint / "pause step" works. (The mechanism is part of the
  result protocol; a concrete pause-step archetype is a consumer feature, not part of the core model.)

- **Outcome taxonomy.** Every boundary resolves the execution to one of:
  - **success(value)** — terminal, carrying the typed output tuple,
  - **failed(message)** — terminal,
  - **cancelled** — terminal,
  - **paused(reason)** — **non-terminal**, resumable from where it left off.

- **Auto-run ("slow motion") is client-paced, not engine-driven.** An interactive client may drive a run by
  issuing **step-into or step-over, one boundary at a time, on a timer**, settling between ticks. The loop
  **stops** on a terminal outcome, an *explicit* pause, or an *error* pause, and **continues** on an
  ordinary *boundary* pause. Pacing and settle-polling are a **client** concern; the engine only needs to
  make each step's result observable as it happens.

- **Interactive request/response into a running execution.** Beyond passively observing the trace, a caller
  can send an **on-demand request addressed to a specific live execution** and get a response (a duplex
  query — e.g. "give me the current slice of your output"). The model must let a running Logic **answer
  requests** without leaving its boundary discipline. This is the *pull* half of interactivity; tracing
  (§7) is the *push* half.

---

## 5. Live edit and state migration

Both halves of this section are **core, use-case-agnostic requirements** — any Logic implementation inherits
them; they must not be re-implemented per flavour.

- **Edit while paused.** When a run is paused, the user may edit the **logic definition** and resume against
  the **new** definition. The live (possibly-edited) definition is **re-read at every boundary**, not only
  in response to an explicit "reload" — so resuming naturally picks up edits. Edits arrive through the
  definition's normal mutation channel (CQRS); the model only requires that the current definition is
  consulted afresh each tick.

- **Identity continuity across structural edits.** Edits are not just value tweaks — an element may be
  **renamed, moved, added, or removed**. In-flight execution state and the trace must **follow an element
  through a rename/move**, so a stepped-through or part-run element keeps its state and history when its
  address changes. This requires a **stable identity** for elements that is independent of their
  (mutable) address.

- **State migration.** On resume after an edit, in-flight state must carry across **where it remains
  coherent**, and reset cleanly where it does not:
  - State that survives includes accumulators, open resources (file handles, processes), buffered in-flight
    data, and paused sub-executions.
  - An element matches its predecessor **by stable identity** and adopts its captured state; an element the
    edit **added** starts fresh; one the edit **removed** is disposed.
  - **Capture must be able to run *before* teardown** of the old execution — so a live resource can be
    *detached* and handed to the new instance rather than being closed by teardown and lost.
  - Migration must work for **concurrent** executions: rebuilding a parallel computation may mean
    snapshotting many spines at a quiescent wavefront, then reconstructing them.
  - Migration is **best-effort by contract**: an element that does not opt into carrying specific state
    restarts cleanly with the new definition (the safe default).

- **Documented limitation.** Migration assumes a **single linear history** of edits. Revert / branching /
  version-control of the definition mid-run is not supported (it would require restarting execution).

---

## 6. Resources

- A Logic may acquire **long-lived external resources** — a browser, a file handle, a spawned process —
  that must be **deterministically released** when execution settles to a terminal state.

- **Per-resource close policy.** Each resource declares what happens at termination:
  - **auto** — dispose on completion (success, failure, or cancel),
  - **manual** — never auto-dispose; only an explicit closing action disposes it (survives a forgotten
    close),
  - **keep-on-failure** — dispose on success/cancel, but **retain on a failed run** for inspection.

- **Resource scopes are attached at a level in the execution tree — not only the whole run.** This is a
  first-class requirement: it must be possible to own a resource at the **top level** *and* own a
  **separate** resource of the same kind **per child**. (Concretely: a top-level system-under-test browser,
  **plus** a distinct browser instance for each sub-script.) Disposing a frame disposes the resources scoped
  to it; the run-global scope is just the special case of the root frame.

  > Today the resource scope is run-global (a single `disposeAll`). Tree-scoped ownership is the target.

---

## 7. Observability (tracing)

Observability is **intrinsic to the model**: an interactive computation is one that can be *watched while it
runs* and *reviewed after it ends*. Every executing element therefore has a first-class way to **record what
it is doing**, and the recording is part of the Logic contract — not an optional add-on.

- **Two write modes**, both required:
  - **Live state — latest-value-per-address.** The *current* value at an address, overwritten as it
    changes, and **resettable** (e.g. a loop body clears its per-iteration state each pass so only the
    latest survives).
  - **Append-only history.** An immutable, ordered timeline of events that **survives resets** — the
    "film-strip" of everything that happened, including each loop iteration and each nested execution.

- **Typed trace values, including large binary.** Trace values are typed (text, number, boolean, list, map,
  **binary**, …) — binary because real consumers record screenshots and other blobs. Arbitrary values must
  be recordable.

- **Push and incremental pull.** Live observers can be **notified** as values change (push, for a live
  updating view). Large/long histories must be retrievable **incrementally** — a client polls only events
  **newer than a watermark**, so binary blobs already delivered are never re-sent.

- **One definition, many traces — merged and attributed.** The same definition can execute **many times** in
  one run (loop iterations, repeated sub-computations, concurrent workers), and **each execution is a
  distinct trace**. Therefore:
  - A whole run's view is the **merge** of all its executions' traces, with duplicates at the same address
    resolved by the **latest write**.
  - Two invocations of the same definition must remain **distinguishable** — the trace must carry the
    **execution tree** (each execution's parent and call-site) so a consumer can attribute events to the
    *specific* invocation, not just to the definition that ran.

- **Total ordering across parallel spines.** Because executions run in parallel, **wall-clock time is
  insufficient** to order events. The model requires a **global monotonic sequence** across the whole run so
  the merge and the timeline are deterministically ordered.

- **Retention vs. bounding (a real tension).** A finished run's trace must be **kept** so the user can
  review what happened. **But** a streaming / long-running execution cannot retain **unbounded**
  per-iteration / per-element buffers — retention must be **bounded** (e.g. finished frames are evicted;
  history is retained selectively). "Keep the trace" and "don't grow without bound" must both hold.

- **Rename-survival.** Trace addressing must use the **stable identity** of §5, so a trace recorded before a
  rename still resolves to the element's current address when viewed afterward.

---

## 8. Sources of complexity (for the simplification brainstorm)

The individual requirements above are each simple. Today's sprawl comes from where they **interact**. This
section names those interactions as **targets to attack** — open questions, not prescriptions.

- **Global state vs. concurrent/background runs (the headline tension).** Much of the current machinery
  assumes one active run and process-global stores (one controller, one trace store with a global "clear
  all", one identity map, a run-global resource scope). The §2 anti-goal (no global singletons; concurrent
  + background runs) means **all run state must become per-run / per-tree**. *Question: what is the minimal
  per-run context object that owns control + trace + identity + resources, so nothing is global?*

- **Live-edit × parallelism × stepping × stable-identity × retention, all at once.** Each is tractable
  alone; together they force capture-before-teardown migration, stable-id-keyed everything, per-spine step
  state mirrored onto confined children, and quiescence barriers. *Question: which of these can be made
  orthogonal, so a simple sequential Logic doesn't pay for the concurrent/streaming case?*

- **Two trace write modes + bounded retention + total ordering** produce the buffer / merge / evict /
  sequence machinery. *Question: is there a single log abstraction (one ordered event stream per execution,
  with a derived latest-value view) that yields both modes without two parallel structures?*

- **Heterogeneous composition + confinement + step-across-boundaries** produce the per-child control and the
  mirroring of a parent's step plan onto children. *Question: can "host a child Logic" be one primitive that
  the run controller drives uniformly, so flavours add no stepping code?*

- **Tree-scoped resources (desired) vs. run-global disposal (current).** *Question: if a frame is the unit
  of resource ownership, does resource lifecycle just fall out of frame lifecycle?*

- **Core vs. consumer placement.** Behaviour this spec calls core — the run state machine, state migration,
  identity — currently lives partly in kzen-auto (see appendix). *Question: what is the use-case-agnostic
  core surface that kzen-auto's Script/Flow/Job (and future flavours) sit on top of with no duplicated
  orchestration?*

---

## Appendix — as-built type map

How the requirements map onto the **current** implementation, so a reader can locate what exists. Core
abstractions live in `kzen-lib-common/.../exec/`; server primitives in `kzen-lib-jvm`; the run state
machine, the concrete flavours, and the client driver currently live in **kzen-auto** (flagged where the
spec says they belong in core).

| Requirement area | Current types | Where |
|---|---|---|
| Logic unit & lifecycle | `Logic`, `LogicExecution` (`beforeStart` / `continueOrStart` / `close`), `LogicExecutionFacade`, `LogicHandle` / `LogicHandleFacade`, `StatefulLogicElement.loadState` | kzen-lib-common `exec/logic/` |
| Typed I/O | `LogicDefinition`, `TupleDefinition` / `TupleValue` / `TupleComponentDefinition` / `TupleComponentValue`, `TupleComponentName.main` / `.detail`, `LogicType` | kzen-lib-common `exec/logic/`, `exec/tuple/` |
| Control & stepping | `LogicControl` (`pollCommand`, `consumeStepBudget`, `runningFreeByDepth`, `enterFrame` / `exitFrame`, `armedStepBudget` / `armedDepthLimit`, `subscribeRequest`, `pauseOnError`), `LogicCommand` (Cancel / Pause / None) | kzen-lib-common `exec/logic/` |
| Outcomes & states | `LogicResult` (Success / Failed / Cancelled / Paused), `LogicPauseReason` (Boundary / Explicit / Error), `LogicRunState`, `LogicStatus`, `LogicRunResponse` | kzen-lib-common `exec/logic/model/`, `run/model/` |
| Run model & identity | `LogicRunId`, `LogicExecutionId`, `LogicRunExecutionId`, `LogicRunFrameInfo` (the tree), `LogicRunExecutionInfo` (parent + caller attribution), `ObjectStableId` + `ObjectStableMapper` | kzen-lib-common `exec/logic/run/model/`, `service/store/normal/` |
| Run controller (**spec: core; today: kzen-auto**) | `LogicController` (start / request / cancel / pause / continueOrStart / step / stepOver / stepOut); impl `ServerLogicController` on a single dedicated thread | iface kzen-lib-common `exec/logic/run/`; impl `kzen-auto-jvm/.../server/service/impl/` |
| Stepping primitive | `MutableLogicControl` (budget + depth-limit `arm`, frame depth) | `kzen-lib-jvm/.../server/exec/logic/context/` |
| Live edit & migration (**spec: core; today: split**) | `StatefulLogicElement` (core); orchestration in `ScriptDocument` / `JobExecution` (capture-before-teardown, channel carryover) | core iface kzen-lib-common; orchestration `kzen-auto-jvm/.../objects/{script,job}/` |
| Resources | `LogicResourceScope` (`register` / `deregister` / `disposeAll`), `ResourceClosePolicy` (Auto / Manual / KeepOnFailure) — **run-global today; spec wants tree-scoped** | kzen-lib-common `exec/logic/` |
| Tracing | `LogicTrace` (lookup / lookupRun / lookupRunHistory / lookupRunExecutions / mostRecent / clear / clearAll), `LogicTraceHandle` (`set` / `append` / `clearAll` / `register`), `LogicTracePath` (+ `$stable` marker), `LogicTraceEntry`, `LogicTraceEvent`, `LogicTraceSnapshot`, `LogicTraceQuery` | kzen-lib-common `exec/logic/trace/` |
| Trace values | `ExecutionValue` hierarchy (Null / Text / Boolean / Number / Long / **Binary** / List / Map) | kzen-lib-common `exec/` |
| Trace store | `LogicTraceStore` — in-memory, `ObjectStableId`-keyed buffers, process-global monotonic sequence, frame-close eviction | `kzen-lib-jvm/.../server/exec/logic/trace/` |
| Interactive request/response | `ExecutionRequest` / `ExecutionResult` / `RequestParams`; `LogicController.request`; `LogicControl.subscribeRequest` | kzen-lib-common `exec/` |
| Example consumers (illustrative only) | `ScriptDocument`, `FlowDocument`, `JobDocument` + `JobExecution` (parallel workers, quiescence, channel-carryover migration); client driver `ClientLogicGlobal` (poll + auto-step) | `kzen-auto-jvm/.../objects/{script,flow,job}/`, `kzen-auto-js/.../service/logic/` |

Related reading: [`architecture.md`](architecture.md) § "Execution model (Logic / Task / Trace)" and
§ "Stable identity"; the Job paradigm plan `kzen/plans/2026-06-23_job-paradigm.md` (the most worked-out
concurrent + migrating consumer); `kzen-auto/docs/architecture.md` §1/§3 (Script as the reference Logic and
the REST run-control surface).
