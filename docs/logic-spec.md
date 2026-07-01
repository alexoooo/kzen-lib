# Logic — functional specification

> **Status: living specification.** This document defines the *functional requirements* of the kzen Logic
> execution framework **independently of how it is currently implemented**. It was written when the
> implementation had grown complex and sprawling (split across `kzen-lib` and `kzen-auto`), to give a
> precise, implementation-agnostic statement of *what must hold* as the basis for a dramatically simpler
> design. **That design has since been built:** a single-writer **`RunEngine`** in `kzen-lib` (the
> use-case-agnostic core) now drives all flavours, and most of the "deliberate targets" below are met —
> tree-scoped resources, engine-owned state migration, per-execution trace attribution, and a per-run
> (no-singleton) engine are all implemented. The residual gaps (multiple concurrent *server* runs; a
> non-singleton trace store) are called out inline. Requirements are stated in §1–§7; §8 records how the
> interacting tensions were resolved; the appendix maps each requirement to the **current** (post-rewrite)
> build. Where §1–§7 and the current code disagree, **the requirement still wins** — the spec leads the
> implementation, not the other way round.

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
  underlying capability. A host **records the call-site** that spawned each child (which element hosted it),
  so a re-used child definition's trace can be attributed to the *specific* invocation (§7). Confinement has
  one deliberate exception: a child may **inherit a specific mutable resource** from its host (a shared
  browser — §6), which is opt-in and explicit, never ambient.

- **Frame tree vs. execution tree.** One node tree serves two views. The **live frame tree** (the nested
  run-status display) shows only executions still *in progress* — a child that ran to completion (a
  stepped-over / stepped-out sub-computation) is pruned from the paused stack depth. The **execution tree**
  (trace attribution, §7) instead retains *every* execution that ran, completed or not, so its trace stays
  addressable after it settles. Same nodes, two projections.

- **No global singletons (explicit anti-goal).** The model must **not** depend on process-global state.
  Concretely: there must be no requirement that *at most one run is active*, no single shared run
  controller, no single shared trace store, no single shared identity map, no run-global "clear everything".
  All run state must be **per-run / per-execution-tree**, so that:
  - **multiple top-level runs can execute concurrently**, fully isolated from one another, and
  - **non-interactive background runs** (no attached UI, no stepping) are possible.

  > **The core `RunEngine` meets this:** a run is a plain object owning all its own state (engine loop, run
  > state, event log, identity counter, resources), so multiple engines can execute concurrently with no
  > shared mutable state, and a background run is just an engine no one is observing. The residual limitation
  > is *above* the core — the server's `ServerLogicController` still tracks a single active run, and the
  > `LogicTraceStore` is still a shared service with a run-global `clearAll`. Those are the last two places
  > to make per-run; the engine no longer forces the singleton.

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

- **Launch state — created, launched, start-paused, start-stepping.** A run is first **created** (its state
  exists) but **not launched**; the first control command launches it. Launching is itself controllable: a
  run can be **launched paused at entry** — parked at its first boundary *before* any progress (the
  debugger's "start paused") — and **start-stepping** launches *and* runs **exactly the first step**,
  settling before the second. Start-stepping is one **atomic** operation, not a launch-then-step race (the
  two would race on the "already running" guard). A never-launched run is idle and cancellable; cancelling
  one settles it *cancelled* without ever running a boundary.

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

- **A no-edit boundary must be a stable no-op.** Because the definition is re-read at *every* boundary, the
  overwhelmingly common case — re-reading an **unchanged** definition — must **not** trigger a migration.
  The change signal must therefore be **deterministic**: derived from the durable, editable *description*
  (the notation), not from a freshly-rebuilt runtime object whose fresh mutable scaffolding never compares
  equal to a prior build of the same description. If a no-op re-read spuriously "detected an edit", every
  plain step/resume would rebuild-and-re-park at the same wavefront and the run could **never advance**.
  (Learned concretely: a compiled Flow definition embeds freshly-constructed mutable channel instances with
  identity equality, so two builds of the *same* notation are never definition-equal — the diff must be over
  notation, not the compiled definition.)

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

- **Step-after-edit re-parks; run-after-edit resumes.** Applying an edit is bounded by the pending command:
  **stepping** after an edit rebuilds onto the new definition and re-parks at its **first** wavefront (a
  step *onto* the edit, not *past* it), while **running** after an edit rebuilds and continues at speed. In
  both cases the run's **history, sequence, observers and terminal handle survive the rebuild** — the trace
  is continuous across the edit; only the execution tree is reconstructed.

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

  > **Implemented.** Each resource is registered on the **node** that opened it and disposed when *that* node
  > settles (per its close policy); the run-global scope is just the root node's. A resource an explicit
  > closing step disposes itself is **deregistered** first, so the auto-disposer never double-fires.

- **Resource inheritance along the host chain.** A hosted child may **share a specific resource with its
  host** rather than opening its own — the same browser instance a parent Script opened is the one its
  sub-scripts drive. This is the explicit, opt-in exception to confinement (§2): the shared resource stays
  **owned (and disposed) by the frame that opened it**; the child only borrows the live handle for the
  duration of the host, and does not dispose it on its own settle.

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

- **Live per-invocation scoping (no ghosting on re-entry).** The merged whole-run view (latest-write-wins)
  is for **post-run inspection** — it shows the *most recent* invocation of a definition. A **live** view of
  a currently-executing element must instead be scoped to **that element's own current execution**
  (addressed by its execution id in the tree), isolated from prior and sibling invocations of the same
  definition. So when a sub-Logic is **re-entered** (a loop body, a repeated sub-computation), its live
  latest-value view **starts clean** — the previous invocation's finished per-step values must not linger
  ("ghost") into the new one — while the append-only *history* of the earlier invocation is retained. Each
  invocation being a distinct, individually-addressable execution is precisely what makes this scoping
  possible; the run-merge is a projection over all of them, not the primary live view. (This is why a shared
  run buffer keyed only by element identity is wrong: it flattens invocations and ghosts on re-entry.)

- **Total ordering across parallel spines.** Because executions run in parallel, **wall-clock time is
  insufficient** to order events. The model requires a **global monotonic sequence** across the whole run so
  the merge and the timeline are deterministically ordered.

- **Retention vs. bounding (a real tension).** A finished run's trace must be **kept** so the user can
  review what happened. **But** a streaming / long-running execution cannot retain **unbounded**
  per-iteration / per-element buffers — retention must be **bounded** (e.g. finished frames are evicted;
  history is retained selectively). "Keep the trace" and "don't grow without bound" must both hold.

  > **Wired.** Retention is the **default**, and bounding is **opt-in per host** — so the two requirements
  > coexist rather than trade off. A finished frame's buffer is kept by default, so post-run review works (a
  > Script `RunStep`'s screenshot strip shows *every* loop iteration's finished sub-script). A long *streaming*
  > host that opens one child per element passes `Execution.host(…, retainTrace = false)`, recorded as
  > `Node.retainTrace`; the engine bridge then **evicts that frame's per-execution buffer the moment it settles
  > terminal**, bounding the run to its live frames instead of leaking one buffer per element. Re-entry still
  > clears a prior invocation's live values (so a loop doesn't grow the latest-value view), and a new run clears
  > all prior traces. The bound is a per-host choice a third-party Logic sets — not a hard-coded engine policy —
  > so it can never silently drop a frame a consumer wanted to review.

- **Rename-survival.** Trace addressing must use the **stable identity** of §5, so a trace recorded before a
  rename still resolves to the element's current address when viewed afterward.

---

## 8. Sources of complexity — and how they were resolved

The individual requirements above are each simple; the old sprawl came from where they **interact**. This
section named those interactions as targets to attack. Each is now answered by the `RunEngine` design —
recorded here so the rationale isn't lost and future changes don't regress it.

- **Global state vs. concurrent/background runs (the headline tension).** The old machinery assumed one
  active run and process-global stores. *Question: what is the minimal per-run context object that owns
  control + trace + identity + resources, so nothing is global?*
  **Resolved:** the per-run context *is* the `RunEngine` instance — it owns the node tree, run command,
  event log, identity counter and resource registrations under a single lock (the single writer). Nothing in
  the core is global; the only residual singletons are the server controller and the trace store (§2 note).

- **Live-edit × parallelism × stepping × stable-identity × retention, all at once.** *Question: which of
  these can be made orthogonal, so a simple sequential Logic doesn't pay for the concurrent/streaming case?*
  **Resolved:** the engine makes them orthogonal. A sequential Logic writes plain coroutine code
  (`for`/`while`) and declares boundaries with `checkpoint()`; concurrency, step policy, quiescence,
  capture-before-teardown migration and stable-id keying all live in the engine. A simple Logic pays for
  none of it, and migration is a single `migrate` barrier over the quiescent tree.

- **Two trace write modes + bounded retention + total ordering.** *Question: is there a single log
  abstraction (one ordered event stream per execution, with a derived latest-value view) that yields both
  modes without two parallel structures?*
  **Resolved:** yes. There is one ordered event log per run (the `TraceEvent` stream, ordered by the
  single-writer `sequence`); the live latest-value view (`Node.live`) is a *projection* of it (last write
  per address). One stream, two views — no second parallel structure. Streaming *bounding* is layered on top
  without a second structure: a host opts a child frame out of retention (`Execution.host(retainTrace = false)`
  → `Node.retainTrace`) and the engine bridge evicts that per-execution buffer on frame close, so retention
  (default) and bounding (opt-in) coexist — see the §7 retention note.

- **Heterogeneous composition + confinement + step-across-boundaries.** *Question: can "host a child Logic"
  be one primitive that the run controller drives uniformly, so flavours add no stepping code?*
  **Resolved:** `Execution.host` is the single composition primitive, and the engine computes step-into /
  over / out centrally from the tree's depth — so flavours add **no** stepping code; a Logic only calls
  `checkpoint()`.

- **Tree-scoped resources (desired) vs. run-global disposal (old).** *Question: if a frame is the unit of
  resource ownership, does resource lifecycle just fall out of frame lifecycle?*
  **Resolved:** yes — a resource is registered on its node and disposed when the node settles (per close
  policy); run-global is just the root node. Resource lifecycle falls out of node lifecycle (§6).

- **Core vs. consumer placement.** *Question: what is the use-case-agnostic core surface that
  Script/Flow/Job (and future flavours) sit on top of with no duplicated orchestration?*
  **Resolved:** the core surface is `Logic` + `Execution` + `Run`/`RunEngine` in `kzen-lib`. Script, Flow,
  Job and Report are `Logic` implementations in kzen-auto with no duplicated run/step/migration
  orchestration; the run state machine, migration and identity are now core.

---

## Appendix — as-built type map

How the requirements map onto the **current** (post-rewrite) implementation, so a reader can locate what
exists. The use-case-agnostic core is the single-writer **`RunEngine`** plus the `Logic` / `Execution` /
`Run` contract in `kzen-lib`; the concrete flavours and the REST-facing controller live in **kzen-auto**.
Behaviour the earlier draft placed "in kzen-auto but spec says core" — the run state machine, state
migration, identity — is now **core**. (The removed pre-rewrite layer — `LogicExecution`, `LogicControl` /
`MutableLogicControl`, `LogicResult` / `LogicPauseReason`, `StatefulLogicElement` — no longer exists.)

| Requirement area | Current types | Where |
|---|---|---|
| Logic unit | `Logic` (`run(execution): TupleValue`, `signature()`), `LogicSignature`, `LogicDefinition` | kzen-lib-common `exec/engine/`, `exec/logic/model/` |
| Execution context (the whole surface a Logic touches) | `Execution` — `inputs`, `checkpoint`, `emit`, `log`, `pauseHere`, `recoverable`, `host`, `resource` / `releaseResource`, `onRequest`, `onCapture` / `restored` | kzen-lib-common `exec/engine/` |
| Engine (**now: core**) | `RunEngine` (single-writer; owns node tree, event log, identity, resources, migration; `awaitQuiescent`, `migrate`), `CountingDispatcher` (quiescence / busy-vs-idle) | kzen-lib-jvm `server/exec/engine/` |
| Execution tree & state | `Node` (id + stableId + status + live + children + **callerStableId** + **retainTrace** — frame *and* execution tree; `retainTrace` governs frame-close trace eviction, §7), `NodeId`, `NodeStatus` (Running / Suspended(reason) / Terminal(outcome)), `RunState` | kzen-lib-common `exec/engine/` |
| Run-control handle | `Run` (snapshot / observe / resume / pause / cancel / step(mode) / pauseOnError / request / history / await) | kzen-lib-common `exec/engine/` |
| Typed I/O | `TupleDefinition` / `TupleValue` / `TupleComponentDefinition` / `TupleComponentValue`, `TupleComponentName.main` / `.detail`, `LogicType` | kzen-lib-common `exec/tuple/`, `exec/logic/model/` |
| Stepping, pause reasons, outcomes | `StepMode` (Into / Over / Out), `PauseReason` (Boundary / Explicit / Error), `Outcome` (Success / Failed / Cancelled) | kzen-lib-common `exec/engine/` |
| Run controller (REST bridge onto the engine) | `LogicController` (start / status / request / cancel / pause / continueOrStart / step / stepOver / stepOut) + `ServerLogicController` extras (`startStep`, `setPauseOnError`); the impl drives the engine on a single thread, mirrors trace per-node, and detects live edits | iface kzen-lib-common `exec/logic/run/`; impl kzen-auto-jvm `server/service/impl/` |
| Run / execution identity | `LogicRunId`, `LogicExecutionId`, `LogicRunExecutionId`, `LogicRunInfo`, `LogicRunFrameInfo` (live frame tree), `LogicRunExecutionInfo` (parent + call-site attribution), `LogicRunState` / `LogicStatus` / `LogicRunResponse`, `ObjectStableId` + `ObjectStableMapper` | kzen-lib-common `exec/logic/run/model/`, `service/store/normal/` |
| Live edit & migration (**now: core**) | `RunEngine.migrate` (capture-before-teardown, rebuild-by-stable-id, orphan sweep) + `Execution.onCapture` / `restored`; edit-**detection** by notation-diff over the transitive closure in `ServerLogicController.pendingMigration` | engine kzen-lib-jvm; detection kzen-auto-jvm |
| Resources (**now: tree-scoped**) | `Execution.resource` / `releaseResource` (per-node, disposed on node settle), `ClosePolicy` (Auto / Manual / KeepOnFailure) [engine], `ResourceClosePolicy` [notation-level] | kzen-lib-common `exec/engine/`, `exec/logic/` |
| Tracing | `LogicTrace` (lookup / lookupRun / lookupRunHistory / lookupRunExecutions / mostRecent / clear / clearAll), `LogicTraceHandle` (set / append / clearAll / register), `LogicTracePath` (+ `$stable` marker), `LogicTraceEntry` / `LogicTraceEvent` / `LogicTraceSnapshot` / `LogicTraceQuery`; engine-side `TraceEvent` (sequence, nodeId, stableId, address, value) + `Address` | kzen-lib-common `exec/logic/trace/`, `exec/engine/` |
| Trace values | `ExecutionValue` hierarchy (Null / Text / Boolean / Number / Long / **Binary** / List / Map) | kzen-lib-common `exec/` |
| Trace store | `LogicTraceStore` — in-memory, `ObjectStableId`-keyed **per-execution** buffers (one per node), monotonic sequence, same-stable-id re-entry clearing + per-execution `evict` on frame close for opt-out (`retainTrace = false`) streaming frames — both wired; the engine event log is bridged into it **one buffer per node** by `ServerLogicController.mirrorTrace`, which also evicts closed opt-out frames (`evictClosedFrames`) | store kzen-lib-jvm `server/exec/logic/trace/`; bridge kzen-auto-jvm |
| Interactive request/response | `ExecutionRequest` / `ExecutionResult` / `RequestParams`; `Run.request` / `LogicController.request`; `Execution.onRequest` | kzen-lib-common `exec/` |
| Example consumers (illustrative only) | `ScriptLogic` / `ScriptRunContext`, `FlowLogic` / `FlowRun`, `JobLogic` / `JobRun` / `WorkerLogic` / `EngineJobControl`, `ReportLogic` / `ReportRun`; `LogicCompiler` (document → `Logic`); client driver `ClientLogicGlobal` (poll + auto-step) | kzen-auto-jvm `server/exec/**`, `server/objects/**`; kzen-auto-js `client/service/logic/` |

Related reading: [`architecture.md`](architecture.md) § "Execution model (Logic / Task / Trace)" and
§ "Stable identity"; the Job paradigm plan `kzen/plans/2026-06-23_job-paradigm.md` (the most worked-out
concurrent + migrating consumer); `kzen-auto/docs/architecture.md` §1/§3 (Script as the reference Logic and
the REST run-control surface).
