# Logic — functional specification

> **Status: living specification.** This document defines the *functional requirements* of the kzen Logic
> execution framework **independently of how it is currently implemented**. It was written when the
> implementation had grown complex and sprawling (split across `kzen-lib` and `kzen-auto`), to give a
> precise, implementation-agnostic statement of *what must hold* as the basis for a dramatically simpler
> design. **That design has since been built:** a single-writer **`RunEngine`** in `kzen-lib` (the
> use-case-agnostic core) now drives all flavours, and most of the "deliberate targets" below are met —
> tree-scoped resources, engine-owned state migration, per-execution trace attribution, and a per-run
> (no-singleton) engine are all implemented. Requirements are stated in §1–§7; §8 records how the
> interacting tensions were resolved; the appendix maps each requirement to the **current** (post-rewrite)
> build. Where §1–§7 and the current code disagree, **the requirement still wins** — the spec leads the
> implementation, not the other way round.
>
> **Known gaps, each called out inline where it belongs** (last reconciled against the build 2026-07-25):
> - **Multiple concurrent *server* runs** — the engine imposes no singleton, but the server controller
>   still tracks a single active run (engine plan E6, **deferred**; §2).
> - **Run-level pause-reason reduction** — the required Error > Explicit > Boundary rule is not what the
>   current projection computes, which matters only for concurrent flavours (§4).
> - **Streaming trace bounding is implemented but unadopted** at the per-frame grain, and Flow / Job
>   progress emits still grow history without bound (§7).
>
> Two model affordances are likewise **reserved but unconsumed**: the tuple-level `detail` output
> component (§3) and the engine's frame-close / reset observer signals (appendix).

## What a Logic is

A **Logic** is kzen's abstraction for **general-purpose interactive computation**: a typed, stateful
computation that can be **run, paused, stepped, resumed, and cancelled**; that is **observable** (it emits a
trace as an intrinsic part of running); that can be **edited while paused and resumed against the new
definition**; and that **composes** — a Logic can host other Logics as confined children.

The model is **agnostic to any particular flavour of logic.** kzen-auto's four flavours — **Script**
(sequential steps), **Flow** (synchronous vertex DAG), **Job** (concurrent workers over channels), and
**Report** (a lock-free record pipeline) — are **example consumers**, not part of the model. The
requirements below are the *generalization* of what those four need; the design must support all of them
**without constraining future implementations** of interactive computation (a debugger, a notebook, a
state machine, a long-running daemon, …).

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
    tell "busy" from "idle". *As built:* a spine wraps a blocking third-party call (Selenium, JDBC, large
    file I/O) in **`Execution.blocking { }`**, which offloads it to a per-engine elastic pool (freeing the
    fixed dispatcher thread) while keeping it counted as in-flight — so a spine parked inside a blocking call
    reads as *busy* to the quiescence barrier, never falsely idle (the same accounting a pending `delay`
    gets). The block is run interruptibly, so cooperative cancel reaches it too.

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
  addressable after it settles — **except** one whose host explicitly opted out of retention (the §7
  streaming bound), which is compacted out of the tree when it settles. Same nodes, two projections.

- **Quiescence is not liveness — deadlock detection is deliberately *not* core.** A run whose every spine
  is parked and a run whose every spine is permanently blocked look identical to the core: both are
  quiescent. The model therefore makes **no liveness guarantee** and provides no watchdog; detecting that
  a computation can provably make no further progress requires knowing *what* the spines are waiting on,
  which is flavour knowledge. A flavour that can be stuck (a dataflow paradigm whose workers block on
  channels) owns its own detector, reads its own wait state rather than raw quiescence, and must run it
  **off** the engine's dispatcher — a polling coroutine *on* it would keep the run perpetually non-quiescent
  and break pause / step / migrate outright.

  > *As built:* a core engine watchdog was tried and retired — its topology heuristic sat in the
  > use-case-agnostic core and still missed a lone sink on an orphan channel. Its replacement,
  > `JobDeadlockMonitor`, is Job-scoped, polls the run's channels' blocked-endpoint counts against the live
  > non-terminal Worker count on its own daemon thread, requires the verdict to hold across a grace window,
  > and is suppressed entirely while an external duplex channel is open (a Worker idling on a serve port
  > awaits a UI request, not a peer).

- **No global singletons (explicit anti-goal).** The model must **not** depend on process-global state.
  Concretely: there must be no requirement that *at most one run is active*, no single shared run
  controller, no single shared trace store, no single shared identity map, no run-global "clear everything".
  All run state must be **per-run / per-execution-tree**, so that:
  - **multiple top-level runs can execute concurrently**, fully isolated from one another, and
  - **non-interactive background runs** (no attached UI, no stepping) are possible.

  > **The core `RunEngine` meets this:** a run is a plain object owning all its own state (engine loop, run
  > state, event log, identity counter, resources), so multiple engines can execute concurrently with no
  > shared mutable state, and a background run is just an engine no one is observing. The residual limitation
  > is *above* the core — the server's `ServerLogicController` still tracks a **single active run**
  > (`stateOrNull`), and its trace surface (`RunEngineLogicTrace`) projects a **single retained run** via
  > `activeRun()`. Those are the last places to make per-run; the engine no longer forces the singleton.
  > (The former shared `LogicTraceStore` with a run-global `clearAll` was **retired 2026-07-15** by engine
  > plan E4 — trace is now served straight off the retained engine.) Making these per-run is engine plan
  > **E6 (multiple concurrent runs), which is deferred** — not yet needed for the product; the groundwork
  > is verified in place (see `kzen/plans/2026-07-25_master-plan.md` § "Deferred & resolved" for the
  > condensed readiness verdict + friction list; historical detail in
  > `kzen/plans/sprint-1/2026-07-05_logic-engine-improvements.md` Phase 6).

---

## 3. Typed inputs and outputs

- A Logic declares a **typed signature**: inputs and outputs are each a **named, typed tuple** (not
  positional parameters).
- An invocation accepts **zero or more** input components and produces:
  - **void** (zero output components),
  - a single **`main`** output component (the common case), or
  - **several named** output components.
- Auxiliary/observational output (a rich value for the UI) must be expressible **distinctly from the
  primary `main` result**, so a consumer can show it without mistaking it for the computation's value.
  A conventional **`detail`** component name is reserved in the tuple model for this.

  > *As built:* the tuple-level `detail` component is **reserved but unadopted** — `TupleComponentName.detail`
  > and its `ofDetail` / `ofVoidWithDetail` / `detailComponentValue` helpers exist in `kzen-lib` with no
  > consumer. The requirement is met today at the **trace** level instead: a Script step attaches a rich
  > value (a screenshot) to its own element's live trace value (`StepExecution.traceDetail` → the
  > `StepTrace.detail` field), which is where the UI reads it. Either surface satisfies the requirement;
  > only one is currently wired.

- **Declared inputs are addressable, defaulted elements of the definition — not just a signature.** An
  input is declared as a first-class, renameable element carrying its name, its type and an optional
  **default**; an invocation that binds no argument for it resolves to that default. The declaration is
  **flavour-neutral** — every flavour that accepts inputs reads the same notation contract — and the
  **resolved** value of each declared input is surfaced to the trace once per (re)launch, so an editor can
  show what a run actually ran with beside what was declared.

  > *As built:* the shared notation contract is a `parameters` branch of typed declarations plus a
  > `results` map (`LogicConventions.parametersAttributePath` / `resultsAttributePath`, parsed by
  > `ParameterDefaultDefiner` / `ResultSignatureDefiner`), compiled to a flavour-neutral `LogicParameter`
  > (stable id + name + typed default) and surfaced by `LogicParameterTrace` as a bounded display string at
  > the parameter's own stable-id address, written **transiently** (§7) since only the live view reads it.
  > Script and Job both consume it; a Flow binds its host vertex's wired inputs positionally against the
  > callee's declared parameter order.

- Types are **first-class** (carried as type metadata) and used to **validate wiring before running** where
  the flavour allows it (definition-time errors surfaced to the user), rather than failing as a runtime
  cast. The model must permit **user-defined and dynamically-discovered** component types.

---

## 4. Run control

Run control is expressed against a **specific run** (identified per-run, never "the" run), so every command
below is addressed and concurrent runs are independently controllable.

- **Run** — execute at full speed to the next halt (terminal, or a pause).
- **Pause** — settle at the next boundary into a quiescent state. Pause **overrides an in-flight stepping
  command**: a long step (e.g. a step-over of a sub-computation) parks at its next boundary instead of
  running the step to completion — pausing is never refused, and never has to wait for a step to finish.
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

- **Observable run state, and the retained-but-inactive settled run.** A driver projects the run as a small
  state enum a client can act on directly: *running* / *stepping* (work released and in flight),
  *pausing* / *cancelling* (a signal accepted but not yet settled), and the three settled states that
  mirror the pause reasons above — *paused* (an auto-step loop keeps going) versus *explicit-paused* /
  *error-paused* (stop and wait). The in-flight states are **driver** state: the engine cannot see that a
  release is a step rather than a run, nor that a pause has been requested, so the driver must announce
  every settle itself — the engine's own change signal fires *before* the driver reflects the settle, so
  without that announcement the *stepping → paused* transition (exactly what an interactive client waits
  on) would never be pushed.

  A run that reaches a terminal outcome is **retained for post-run trace review but reported as
  no-active-run**: the client stops driving it, resource eviction that gates on "no run active" proceeds,
  and the "clear trace" control becomes available — while §7's queries still project it. Only the next
  launch, or an explicit clear, disposes it.

- **What a "boundary" is.** Each Logic **defines its own unit of progress** — its step/checkpoint
  granularity. A boundary is a point where the computation is in a coherent, observable, pausable state
  (a Script's step, a Flow's vertex, a Job's batch wavefront). Stepping advances one such boundary; the
  unit is the flavour's choice, and the model must not hard-code a single notion of "a step".

  A boundary *may* **name the element it settles on** (`checkpoint(at:)` — a Script step's stable id, a
  Flow vertex's): the engine records it, park or no park, as the node's current **position** (`Node.position`
  — the last named boundary) and surfaces it in run snapshots, so "the element about to run" is engine
  state rather than a per-flavour reserved trace marker. An anonymous boundary (`at = null`) leaves the
  recorded position unchanged — a Logic's internal pausability checkpoints don't blank it; position clears
  only when the node settles.

- **Pause-on-error** — a per-run option, **live-togglable while paused**, taking effect at the next
  boundary: when on, a recoverable failure **pauses** the run for fix-and-resume instead of ending it.

- **Distinct pause reasons.** A pause carries *why* it happened, and the reason **propagates upward
  unchanged** through nested logic (a pause deep inside a child surfaces with its real reason):
  - **boundary** — the ordinary step-settle (an auto-step loop keeps going),
  - **explicit** — a deliberate halt at this element: the computation paused *itself* (a pause-step) or a
    **breakpoint** on the boundary's element fired,
  - **error** — a failure paused under pause-on-error (a deliberate halt).

  The distinction is functional: interactive clients treat *boundary* (keep advancing) differently from
  *explicit* / *error* (stop and wait for the user).

  **Reducing many reasons to one.** Because concurrent spines park independently, a settled run can hold
  several reasons at once (a Job with one Worker error-parked and the rest at their ordinary boundaries).
  The run-level reason a client acts on must be the **most demanding** of them — *error* over *explicit*
  over *boundary* — so that a single failure anywhere halts an auto-step loop rather than being masked by
  siblings that merely settled.

  > **Gap.** The current projection (`ServerLogicController.deepestPauseReason`) instead takes the first
  > reason found by a depth-first walk over reversed children — deterministic, but it picks by tree
  > position rather than by severity, so an error-parked spine can be masked by a later sibling parked at
  > an ordinary boundary. Single-spine flavours (Script, Flow) are unaffected; a concurrent Job is.

- **Internal (self-)pause.** A Logic can pause itself by resolving a boundary as *paused (explicit)* rather
  than continuing — the mechanism by which a "pause step" works. (The mechanism is part of the result
  protocol; a concrete pause-step archetype is a consumer feature, not part of the core model.)

- **Breakpoints — engine policy over positions.** A run holds a **run-scoped, volatile** set of element
  positions (stable ids), updated as a whole (**replace-set** — a control verb like pause, usable before
  launch and mid-run). Reaching a **named** boundary whose element is in the set halts the run: the arriving
  execution settles *paused (explicit)*, and the run's command drops to *paused* so every concurrent
  execution settles at its own next boundary (**stop-the-world**, mirroring an external pause). The check
  applies **regardless of the in-flight command** — running, paused, or stepping: a boundary settle that
  lands on a breakpoint is *upgraded* to explicit, which is what makes the auto-run client loop (below) halt
  on breakpoints for free. Breakpoints are never persisted with the definition and are cleared with the run
  (an interactive client re-pushes them at run start); being stable-id keyed, they survive rename and
  live-edit migration untouched. The check happens **on arrival** at the boundary: resuming from a
  breakpoint park proceeds past it, and a persistent breakpoint re-triggers on the next arrival (e.g. a
  loop's next iteration). "Run to an element" is a client composition, not a separate mechanism: add a
  breakpoint at the target, run, remove it.

- **Repositioning (move-to) — a flavour-owned self-migration.** A driver may **reposition** a paused run to
  a named element (a debugger's "set next statement"): move the run's position to a target *without*
  executing the intervening elements. The engine's whole role is to carry that target — an opaque element
  position (stable id) — as a **one-shot hint across the §5 migration barrier** (`migrate(moveTarget = …)`,
  surfaced to the rebuilt tree as `Execution.moveTarget`); the actual reposition is **flavour-owned state
  surgery performed at restore** (§5), because only the flavour knows its own outcome/replay model. A Logic
  that supports it advertises so **structurally** (`Repositionable.canMoveTo`, checked before the barrier is
  torn down, so an illegal target is rejected without disrupting the run); a Logic that does not — or one in
  whose structure the target does not resolve — **ignores** the hint, leaving an ordinary migrate parked at
  its existing frontier. Like breakpoints, the target is stable-id keyed, so it is rename / live-edit safe.

  Repositioning is the one control verb that is **refusable**: unlike run / pause / step, which are always
  accepted, a reposition whose target the current definition cannot honour must be **rejected with the run
  left untouched** — nothing torn down, no state lost — which is why the capability check happens before
  the barrier. It is also the one verb that **always re-reads the definition** (a reposition *is* a
  migrate, so it shares its barrier with any pending edit and the two land in a single rebuild; a failure
  to rebuild is likewise a refusal, not a run-ending error). It is permitted while paused **or**
  error-parked — jumping past a failing element is a headline use — and refused while running. Asking to
  move to where the run is already parked is a no-op, not a rebuild.

- **Outcome taxonomy.** Every boundary resolves the execution to one of:
  - **success(value)** — terminal, carrying the typed output tuple,
  - **failed(message, at)** — terminal; **`at` names the element the failure originated at**, and — exactly
    like a pause reason — **propagates upward unchanged** as the failure flattens up the host chain, so a
    whole-run failure still says where it really came from rather than naming the outermost frame. The
    engine stamps a fresh throwable with the failing node's own identity; a re-thrown child failure keeps
    the child's. Null when the origin is unknown.
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

  Answering is **not** a boundary: the handler runs on the caller's thread while the execution keeps
  running, so it must be safe to call concurrently and should read a snapshot the execution publishes
  rather than the execution's live state. A response must therefore be **bounded in time** — a handler
  that waits on a paused or slow execution would otherwise hold the caller (and, if the driver serializes
  control, the run's whole control surface) indefinitely; a timeout that surfaces as a failed response is
  the required behaviour, not a stalled one.

  > *As built:* a Job registers one router on its **root** node (the frame a remote client addresses) and
  > dispatches inbound requests **by channel name** to the Worker serving that `external` duplex channel,
  > with a 1-second cap on the round trip — a deliberate bounded seam, since the controller call is
  > synchronized and blocking. Report registers its preview handler on its own node directly.

---

## 5. Live edit and state migration

Both halves of this section are **core, use-case-agnostic requirements** — any Logic implementation inherits
them; they must not be re-implemented per flavour.

- **Edit while paused.** When a run is paused, the user may edit the **logic definition** and resume against
  the **new** definition. The live (possibly-edited) definition is **re-read on every release of work**
  (resume, step, reposition), not only in response to an explicit "reload" — so resuming naturally picks up
  edits and no separate reload verb exists. Edits arrive through the definition's normal mutation channel
  (CQRS); the model only requires that the current definition is consulted afresh each time the run is let
  go, while it is quiescent and *before* work is released.

  **What "the definition" spans.** The compared unit is not the root document alone but its **transitive
  closure of linked Logic documents** — everything the root reaches, recursively, including the callees a
  host element points at. Those links are typically *weak* (a call-site names a document without
  structurally depending on it), so a closure derived from strong references alone would miss them and
  editing a paused caller's **callee** would silently fail to migrate the caller. Detection cheats safely:
  a coarse "some notation changed" signal from the mutation channel gates the precise comparison, so a
  clean release — the overwhelming majority, and every tick of a slow-motion run — pays nothing.

  Only a **launched** run migrates: a created-but-unlaunched run has no live state to re-point, so its
  first release simply runs the current definition. And a definition that **fails to rebuild** (a mid-edit
  incomplete state) must **not** end the run — the prior definition keeps running and the reconcile is
  retried on the next release.

- **A no-edit release must be a stable no-op.** Because the definition is re-read on *every* release, the
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
  - **Open resources migrate with their owning frame's stable identity** — a resource registration (§6) is
    lifted off its node at the migration barrier and re-adopted by the rebuilt node that shares the stable
    id, so surviving an edit requires no per-element opt-in; a **removed** frame's resources are disposed at
    the next migration barrier or close (regardless of close policy — no explicit close can reach an owner
    that no longer exists).
  - An element matches its predecessor **by stable identity** and adopts its captured state; an element the
    edit **added** starts fresh; one the edit **removed** is disposed.
  - **Capture must be able to run *before* teardown** of the old execution — so a live resource can be
    *detached* and handed to the new instance rather than being closed by teardown and lost.
  - Migration must work for **concurrent** executions: rebuilding a parallel computation may mean
    snapshotting many spines at a quiescent wavefront, then reconstructing them.
  - Migration is **best-effort by contract**: an element that does not opt into carrying specific state
    restarts cleanly with the new definition (the safe default).
  - **Captured state carries invocation identity.** Several invocations of one hosted definition share a
    stable identity (a loop re-hosting the same sub-document each iteration, two call-sites hosting one
    document), so stable identity alone cannot say *which* invocation a capture belongs to. Three rules keep
    one invocation's state out of another: where invocations **collide on a stable identity** at the
    barrier, the **live (mid-flight) frame's capture wins** over settled ones (a settled frame's capture
    still carries when it is the only one — a flavour that relaunches completed elements, like a Job worker,
    adopts the "done" state instead of redoing the work); a capture is **adopted only by a node hosted from
    the same call-site** as the captured invocation; and a host that re-runs its nested elements live (a
    loop resetting for its next iteration, or restarting) **discards its dropped call-sites' captures** —
    transitively including their descendants' — so a fresh invocation starts clean instead of adopting the
    abandoned one's state (`Execution.discardCaptured`).
  - **Repositioning (move-to) is a self-migration.** A driver-requested move-to (§4 "Repositioning") reuses
    this same barrier with the target carried as a one-shot `Execution.moveTarget`: the flavour performs the
    outcome/replay **state surgery at restore** (dropping the target-and-after outcomes so its position walk
    re-parks at the target — additionally short-circuiting the value-less steps the walk skips over on a
    forward jump, and re-running the target's ancestor containers with their `checkpoint` suppressed so the
    rebuild parks at the target rather than an enclosing boundary), so no new engine machinery is needed — a
    move-to *is* a migrate the flavour steers.

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

- **Per-resource close policy.** Each resource declares what happens at termination. The policy decomposes into
  two orthogonal primitives: a **scope** (which document owns the resource) and a **disposal rule** (how that
  document's outcome disposes it):
  - **scope** — *self* (the document that opened it), *parent* (the calling document one level up, falling back
    to self at the root), or *root* (the overall run);
  - **disposal rule** — *auto* (dispose on completion: success, failure, or cancel), *manual* (never auto-dispose;
    only an explicit closing action disposes it — survives a forgotten close), or *keep-on-failure* (dispose on
    success/cancel, but **retain on a failed** owning document for inspection).

  The notation surface (`ResourceClosePolicy`) exposes these as flat values: `auto` / `manual` / `keepOnFailure`
  (self-scoped), `parent` / `parentKeepOnFailure` (parent-scoped), and `run` / `runKeepOnFailure` (root-scoped).

- **Resource scopes are attached at a level in the execution tree — not only the whole run.** This is a
  first-class requirement: it must be possible to own a resource at the **top level** *and* own a
  **separate** resource of the same kind **per child**. (Concretely: a top-level system-under-test browser,
  **plus** a distinct browser instance for each sub-script.) Disposing a frame disposes the resources scoped
  to it; the run-global scope is just the special case of the root frame.

  > **Implemented.** Each resource is registered on the **node** its scope selects — the opening node (self), its
  > parent, or the root — and disposed when *that* node settles (per its disposal rule); the run-global scope is
  > just the root node's. An opening step can hand a resource up the tree (`parent` / `run`) so it outlives its
  > own document — e.g. a sub-script opens the SUT but binds its lifetime to the enclosing test. A resource an
  > explicit closing step disposes itself is **deregistered** first (searching the opener's ancestor chain, so an
  > ancestor-scoped resource can be released from a descendant), so the auto-disposer never double-fires.
  > A registration may also store the **live handle** (its value), readable from any descendant of the owning
  > node via the same ancestor-chain search — the read side of the inheritance below — and registrations
  > survive live-edit migration keyed by their owning frame's stable identity (§5). A **manual** registration
  > also outlives its owning frame's settle: it hands up to the parent node (cascading toward the root), so it
  > stays on the ancestor chain — readable and explicitly closeable by whatever runs after its opener (the
  > open → use → close split across sibling sub-documents); at the root it leaves the registry alive (the
  > "forgotten close").

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

  > *As built:* one call writes each — `Execution.emit(address, value)` (live, and by default also
  > appended) and `Execution.log(value)` (history only) — and one call resets the live view:
  > `Execution.resetEmitted(addresses, callSites)`, the explicit signal a re-running scope raises (below).

- **Typed trace values, including large binary.** Trace values are typed (text, number, boolean, list, map,
  **binary**, …) — binary because real consumers record screenshots and other blobs. Arbitrary values must
  be recordable.

  **Large binary must not ride the value wire.** A trace snapshot is re-fetched constantly, so inlining a
  megabyte-scale blob in it would re-send that blob on every poll. The wire form of a binary value is
  therefore a **content-addressed handle** — its content hash plus size and media type — and the bytes are
  served **once, out of band**, by their hash. Content addressing (rather than a per-write id) is what
  makes the fetch cacheable and makes an unchanged screenshot free on every subsequent poll, and it keeps
  the substitution **at the wire seam only**: a binary value handed back to a caller in-process keeps its
  bytes.

  > *As built:* `RunEngineLogicTrace.toWireValue` maps a `BinaryExecutionValue` to a
  > `BinaryHandleExecutionValue`; the bytes are resolved by `lookupBinary` (scanning live maps ∪ history)
  > and served by the `/logic/trace-binary` blob endpoint.

- **Push and incremental pull.** Live observers can be **notified** as values change (push, for a live
  updating view). Large/long histories must be retrievable **incrementally** — a client polls only events
  **newer than a watermark**, so binary blobs already delivered are never re-sent.

  > **Wired.** The engine seam is `Run.observe(listener)` — **payload-free** (§2): a notification says
  > *something changed*, never *what*, so it is coalescing-safe and costs the hot path a flag plus a
  > callback. A listener runs on an engine thread and must do nothing but hand off; state comes from
  > pulling `snapshot()` / `history(since)`. Push **out of the process** is the driver's business, not the
  > engine's: exactly one subscription per run is held by the controller, which re-broadcasts to its own
  > observers (an engine-scoped subscription per remote consumer could not survive the run being replaced
  > or disposed, and would keep the engine's observer list growing after `shutdown`).

  **Push must survive leaving the process, and must degrade rather than fail.** The observer above is
  in-process; a real client is remote, so the change signal has to reach it over a transport that can fail
  in ways an in-process callback cannot — dropping, or (worse) *opening successfully and then silently
  delivering nothing* through a buffering intermediary. Two requirements follow. A remote push channel must
  be **proven by delivery, never by connection**, and it must carry a **periodic heartbeat** so an idle-but-
  live channel is distinguishable from a dead-but-open one. And it must be a **strict accelerator over
  polling**: a poll loop stays armed underneath at the cadence that would apply with no push at all, is
  merely relaxed while push is proven, and snaps back the instant push stops being trusted — so every
  failure mode degrades to the pre-push behaviour instead of freezing the view.

  > *As built:* a Server-Sent Events stream (`/logic/events`) carrying the byte-identical status payload the
  > poll fetches, so pushed and polled statuses parse through one client path; a named `ping` event feeds a
  > staleness watchdog; an opened-but-mute stream latches push off for the page (a buffering proxy is a
  > property of the deployment, not a transient fault); only the visible tab holds a stream, to stay under
  > the browser's per-origin connection cap. A client-side throttle then caps how often an arriving status
  > fans out to views — a structure change publishes immediately, a values-only change at a human cadence.

  **Versioned, at more than one granularity — and never by a clock.** Every observable a consumer caches
  against must carry a **monotonic version that moves only when that observable actually moved**, so
  "nothing changed" is decidable without diffing. Wall-clock time cannot serve this role at all: it is not
  monotonic across parallel spines, and a timestamp stamped per call reads as "changed" on every poll,
  which defeats every cache built on it. One version is not enough either, because consumers change at
  different rates: a view of *values* changes on every write, whereas a view of *shape* (which executions
  exist, what state the run is in, which documents hold a trace) changes only occasionally. Collapsing
  both onto the value-rate version forces the shape-keyed consumers to re-fetch full snapshots per write.
  So the status a driver publishes carries **three independent axes**:

  - **value version** — the run's global monotonic trace high-water (the same total order the merge and
    the timeline require, below): a consumer holding version N has, by construction, nothing newer to
    fetch;
  - **structure version** — moves only on a genuine execution-tree change (an execution created or
    destroyed, a run-state transition, a run lifecycle or clear event) and explicitly **not** on a frame
    advancing within a stable set of executions;
  - **epoch** — the transitions neither of the above can express because they happen when there is no
    run to have a version: a run starting, a run settling terminal, a retained trace being cleared. This
    is what lets a consumer notice a post-run "clear" at all — status reports no-active-run both before
    and after it, so without the epoch the response is byte-identical across the clear and no view would
    ever repaint to empty.

  > *As built:* `RunState.sequence` → `LogicRunInfo.sequence` is the value axis; `LogicStatus.epoch` and
  > `LogicStatus.structureVersion` are controller-scoped and present even while `active` is null. The
  > structure version folds the epoch in (so every epoch bump is also a structure bump) and is computed
  > lazily by comparing a cheap signature — epoch, run id, run state, and the *unfiltered* node-id set —
  > off the engine's hot path. It replaced a wall-clock `time` field that conveyed change by being fresh
  > on every call, and therefore made every consumer re-fetch forever.

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
  The reset applies at the **iteration boundary of the re-running host**, not lazily on the next
  invocation's first write: when a scope re-runs its nested elements (a loop starting its next pass), the
  engine's explicit reset signal clears the elements' own live values *and* the retained live values of the
  invocations they previously hosted — transitively — so between the boundary and the fresh pass's first
  write nothing reads as already-run, while every prior pass's history survives.

  > *As built:* `Execution.resetEmitted(addresses, callSites)` — the addresses the host clears on itself,
  > and the call-sites whose hosted invocations (and their descendants) it supersedes. A re-running host
  > raises it alongside `discardCaptured` (§5) over the same element set: the same event has an
  > observability half (clear their traces) and a migration half (abandon their captured state).

- **Total ordering across parallel spines.** Because executions run in parallel, **wall-clock time is
  insufficient** to order events. The model requires a **global monotonic sequence** across the whole run so
  the merge and the timeline are deterministically ordered.

- **An execution's own outcome is observable as trace, not only as run state.** How each element *ended*
  — succeeded, failed (and with what message, and at which element, §4), or was cancelled — is exactly the
  kind of per-element fact a consumer wants beside that element's recorded values, and it must **survive
  the run** so a post-run review still shows it. Because it is derived from the execution's settled state
  rather than written by the Logic, it is **synthesized when read**, on a **dedicated address** that can
  never collide with a value the element itself recorded, and it is **flavour-neutral** — any consumer
  reads any element's outcome the same way, and a flavour that ignores outcomes costs nothing.

  > *As built:* `RunEngineLogicTrace` projects each terminal node's outcome as `OutcomeTrace.toMap` at
  > `LogicTracePath.nodeOutcome(stableId)` — a reserved `$outcome` marker prefix over the same stable id,
  > so it is rename-safe and is dropped when the element is deleted, exactly like an emitted value. The
  > Job UI's per-Worker outcome chip is its consumer.

- **Address translation is a contributed extension, applied at read time.** Within an execution, a Logic
  records values at its own **internal addresses**; the addressing a consumer reads (per-element paths) is
  a different space. Most values map by the obvious rule — the address *is* the element — but a flavour
  may need to record something that is not per-element (whole-pipeline progress, a worker's aggregate
  status). Rather than teach the generic reader about each flavour, a flavour **contributes a routing** for
  a reserved marker it emits; the generic reader dispatches by marker and falls back to the element rule.
  Doing this at **read** time rather than write time is what keeps the write path flavour-free and lets the
  translation change without rewriting anything already recorded.

  > *As built:* `LogicTraceAddressRouting` (`marker` → `tracePath(address, stableId)`), autowired at the
  > composition root and indexed by marker. Job contributes `$job-progress` → the Worker's progress path;
  > Report contributes `$trace-path` → the literal path carried in the remaining address segments (its
  > paths are by-convention, not per-element). A flavour that emits only element addresses contributes
  > none.

- **Retention vs. bounding (a real tension).** A finished run's trace must be **kept** so the user can
  review what happened. **But** a streaming / long-running execution cannot retain **unbounded**
  per-iteration / per-element buffers — retention must be **bounded** (e.g. finished frames are evicted;
  history is retained selectively). "Keep the trace" and "don't grow without bound" must both hold.

  The resolution is asymmetric on purpose: **retention is the default and bounding is opt-in**, chosen by
  the Logic rather than imposed by engine policy — so the engine can never silently drop a frame a consumer
  wanted to review, and a run only pays for bounding when its author knows it streams.

  **Retaining a settled run must cost no resources beyond memory.** A terminated run is kept only to be
  *read*, so keeping it must not hold threads or pools — which makes stopping the machinery and tearing the
  run down two distinct operations: **stop** (release the pools; tree and history stay fully readable) and
  **dispose** (also release anything the run still owns, e.g. state orphaned by an edit). Orphan disposal is
  deliberately deferred by one retention cycle rather than swept eagerly, bounding an orphan's lifetime to a
  single edit without paying a sweep per edit.

  > **Implemented — and how much of it is adopted.** Three bounds exist, at three grains:
  >
  > - **Per-frame (implemented, not yet adopted).** A streaming host that opens one child per element passes
  >   `Execution.host(…, retainTrace = false)`, recorded as `Node.retainTrace`; the engine then **compacts the
  >   settled frame and its subtree out of the run snapshot and its runtime maps**, bounding the live-value /
  >   merged views to live frames instead of leaking one node per element (the frame's `log` events stay in
  >   the history stream). The engine implements and tests this, but **no flavour passes `false` today** — it
  >   is the sanctioned bound waiting for its first streaming consumer.
  > - **Per-emit (adopted).** `emit(retain = false)` updates the live view without appending to history — used
  >   by Script's per-step `StepTrace` (the step's *current* state; only the live view ever reads it) and by
  >   the resolved-parameter display (§3).
  > - **Per-invocation (adopted, and automatic).** Re-entry clears a prior invocation's live values (below),
  >   so a loop does not grow the latest-value view; and a new run disposes the prior retained run.
  >
  > **Open gap:** Flow's per-vertex visual-model emits and Job's per-Worker progress emits are still
  > *retained*, so a long run's history grows without bound — mitigated only by throttling the emit *rate*
  > (wall-clock, per element) and by bounding each payload. Adopting `retain = false` for them is tracked as
  > Job plan Phase 7 (`kzen/plans/2026-07-25_job-improvements.md`); history bounding proper is a later phase.
  >
  > Trace queries read all of this directly off the retained engine — there is no separate trace store to keep
  > in step. `RunEngine.shutdown` / `dispose` are the stop-vs-tear-down split above.
  > (`RunEngine.observeFrames` remains available for a consumer that wants a frame-close signal; it has no
  > production consumer since the trace store was retired.)

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
  the core is global; the only residual singleton is the server controller's single-active-run tracking
  (`ServerLogicController` + its `RunEngineLogicTrace` projection — the former shared trace store was
  retired 2026-07-15; §2 note). Making it per-run is engine plan E6, **deferred**.

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
  single-writer `sequence`); the live latest-value view (`Node.live`, with its per-entry `Node.liveSequence`)
  is a *projection* of it — last write per address, minus any values a re-running scope has reset, plus any
  *transient* (`emit(retain = false)`) writes that update the live view without entering the append-only
  history. One stream, two views — no second parallel *value* store. Streaming *bounding* is layered on top
  the same way: at the *frame* level a host opts a child out of retention (`Execution.host(retainTrace = false)`
  → `Node.retainTrace`); the engine compacts that frame on settle. At the *emit* level `retain = false` keeps a
  high-churn progress signal out of history. Retention (default) and bounding (opt-in, per-frame or per-emit)
  coexist; see the §7 retention note. **The engine is the sole trace store** — the REST trace queries project
  the node tree + event log at query time (translating each flavour's within-node `Address` to its wire
  `LogicTracePath`); there is no second in-memory store bridged in.

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

- **Adding a flavour without editing the framework.** *Question: if flavours add no stepping, migration or
  trace-store code, what is left that a new flavour must touch — and can that be reduced to zero?*
  **Resolved:** yes, to zero framework edits. A runnable document's `main` archetype implements
  `LogicDocument.toLogic`, and `LogicCompiler` resolves that archetype **polymorphically** from the graph —
  there is no flavour `when` anywhere in the run path, so a paradigm is added purely as a notation
  archetype (the flavour-level analogue of the rule that a new Script step is just an object implementing
  `ScriptStep`). Two details are load-bearing rather than incidental: the archetype is instantiated from a
  graph **narrowed to that document's own transitive closure**, so its nested children (a Job's Workers, a
  Script's steps) are never constructed during compilation — which is what lets a Job compile at all, since
  its saved Worker ports are blank until channel synthesis fills them; and the same entry point is what a
  nested host (a Script's run-step, a Flow's run-vertex, a Job's run-worker) compiles its child through, so
  heterogeneous nesting (§2) needs no per-pair knowledge. A document whose `main` is not a `LogicDocument`
  fails to start cleanly rather than escaping as an internal error. The same one-marker-one-contribution
  shape recurs for read-time trace routing (§7).

- **Quiescence looked like it should also answer "is it stuck?".** *Question: the engine already knows when
  every spine is parked — can it not also declare a deadlock?*
  **Resolved: no, and the attempt was retired.** Quiescence is indistinguishable from deadlock without
  knowing what the spines wait on, and a topology heuristic in the core (the original watchdog's
  `>= 2-leaf` rule) both encoded a dataflow assumption in a use-case-agnostic layer and still missed real
  cases. Liveness is now flavour-owned (§2): the detector reads the flavour's own wait state, runs off the
  engine dispatcher, and is suppressed when the run is legitimately idle awaiting an external request. The
  core keeps exactly one liveness-adjacent job — making blocking work *visible* as busy
  (`Execution.blocking`, §2), so quiescence itself is never a lie.

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
| Execution context (the whole surface a Logic touches) | `Execution` — `inputs`; `checkpoint(at:)` (optionally names the boundary's element → `Node.position`); `emit(address, value, retain = true)` (`retain = false` = transient live-only write, absent from history), `log`, `resetEmitted(addresses, callSites)` (live-view reset of a re-running scope, §7); `pauseHere`, `recoverable`, `blocking` (off-dispatcher yet counted busy, §2); `host(stableId, child, inputs, callerStableId, retainTrace)`; `resource` / `resourceValue` (ancestor-chain read) / `releaseResource`; `onRequest`; `onCapture` / `restored` (+ the `restoredAs<T>()` helper) / `discardCaptured` / `moveTarget` (one-shot repositioning hint, §4) | kzen-lib-common `exec/engine/` |
| Engine (**now: core**) | `RunEngine` (single-writer; owns node tree, event log, identity, resources, migration; `awaitQuiescent`, `migrate`, `setBreakpoints`; lazy dirty-flag snapshot, settled-frame compaction; `shutdown` stops the pools while keeping a settled run readable for post-run trace review, `dispose` fully tears down) + `CountingDispatcher` (the quiescence primitive: counts dispatch tasks, and counts a pending `delay` / an `Execution.blocking` region as in-flight so neither reads as idle). Available but unconsumed: `observeFrames` (frame-close signal), `observeResets` / `TraceReset` (synchronous pre-return reset signal) — both left from the retired trace-store bridge | kzen-lib-jvm `server/exec/engine/` |
| Execution tree & state | `Node` (id + stableId + status + live (+ `liveSequence`) + children + **callerStableId** + **retainTrace** + **position** — frame *and* execution tree; `retainTrace` governs frame-close compaction + trace eviction, §7; `position` is the last named boundary, §4), `NodeId`, `NodeStatus` (Running / Suspended(reason) / Terminal(outcome)), `RunState` | kzen-lib-common `exec/engine/` |
| Run-control handle | `Run` (snapshot / observe / resume / pause / cancel / step(mode) / pauseOnError / setBreakpoints / request / history / await; `observe` is a payload-free coalescing change signal — pull `snapshot` / `history` for state) | kzen-lib-common `exec/engine/` |
| Typed I/O | `TupleDefinition` / `TupleValue` / `TupleComponentDefinition` / `TupleComponentValue`, `TupleComponentName.main` (`.detail` is reserved but unconsumed — §3), `LogicType` | kzen-lib-common `exec/tuple/`, `exec/logic/model/` |
| Declared inputs / outputs (flavour-neutral, §3) | notation contract `LogicConventions.parametersAttributePath` / `resultsAttributePath`, parsed by `ParameterDefaultDefiner` / `ResultSignatureDefiner`; compiled to `LogicParameter` (stableId + name + typed default, `resolve(inputs)`), surfaced by `LogicParameterTrace` as a transient per-parameter display emit at run (re)start. Consumed by `ScriptLogicCompiler` / `ScriptLogic` and `JobLogicCompiler` / `JobParameters` / `EngineJobControl.parameter` | kzen-auto-jvm `server/exec/`; kzen-auto-common `paradigm/logic/`, `objects/document/logic/` |
| Stepping, pause reasons, outcomes | `StepMode` (Into / Over / Out), `PauseReason` (Boundary / Explicit / Error), `Outcome` (Success / `Failed(message, at)` — `at` = origin stable id, propagated unchanged through `host` / Cancelled); `OutcomeTrace` (the `{kind, message, at}` wire shape shared by the server projection and the client) | kzen-lib-common `exec/engine/` |
| Run controller (REST bridge onto the engine) | `LogicController` (start / status / request / cancel / pause / continueOrStart / step / stepOver / stepOut) + `ServerLogicController` extras (`startStep`, `setPauseOnError`, `setBreakpoints`, `moveTo` — refusable, returning `LogicRunResponse.Rejected`; `observeStatus`, `retainedTraceAccess`, `clearRetainedTrace`); the impl drives the engine on **one single-thread executor** (each release blocks in `RunEngine.awaitQuiescent` until the run settles, then reflects that back into the status flags; signal-only verbs — pause / cancel / setPauseOnError — call the engine directly so they reach an in-flight run instead of queueing behind it. E6 would need one executor **per run**, since a shared one would serialize unrelated runs), **retains the settled run** for post-run trace queries (disposed on the next `start` / a global clear) while reporting it as no-active-run, and detects live edits. REST surface: `LogicHandler` (+ the `/logic/events` SSE stream and the `/logic/trace-binary` blob route) | iface kzen-lib-common `exec/logic/run/`; impl kzen-auto-jvm `server/service/impl/`, `server/api/handler/` |
| Run / execution identity | `LogicRunId`, `LogicExecutionId`, `LogicRunExecutionId`, `LogicRunInfo` (frame + state + value `sequence`), `LogicRunFrameInfo` (live frame tree + resolved `position`), `LogicRunExecutionInfo` (parent + call-site attribution), `LogicRunState` (Running / Stepping / Pausing / Paused / ExplicitPaused / ErrorPaused / Cancelling), `LogicStatus` (the three version axes of §7: `epoch`, `structureVersion`, `active.sequence`), `LogicRunResponse` (incl. `Rejected`), `ObjectStableId` + `ObjectStableMapper`. (`LogicRunFrameState` is vestigial — its field on `LogicRunFrameInfo` is commented out.) | kzen-lib-common `exec/logic/run/model/`, `service/store/normal/` |
| Live edit & migration (**now: core**) | `RunEngine.migrate` (capture-before-teardown, rebuild-by-stable-id, resource lift/re-adopt, orphan sweep) + `Execution.onCapture` / `restored` / `discardCaptured`; **repositioning** (move-to, §4) via `RunEngine.migrate(moveTarget)` → `Execution.moveTarget`, flavour capability `Repositionable.canMoveTo`; edit-**detection** in `ServerLogicController.pendingMigration` — an event-driven dirty flag (the controller is a `LocalGraphStore.Observer`) gating a content-digest diff over the closure `LinkedLogicDocuments.transitiveDigest` builds from `LogicCallGraph.transitiveCallees` (root document ∪ weakly-linked callees, §5). Per-flavour carried state: `ScriptMigrationState` (completed outcomes + per-step carry + result), `FlowMigrationState` (per-vertex progress + harvested output), Job's channel drain/preload + per-Worker `WorkerBase.captureMigrationState`; Report registers no capture (clean restart — the §5 default) | engine kzen-lib-jvm; detection + flavours kzen-auto-jvm |
| Resources (**now: tree-scoped**) | `Execution.resource(key, policy, scope)` / `releaseResource` (owner node selected by `ResourceScope` = Self / Parent / Root, disposed on that node's settle; release searches the ancestor chain), `ClosePolicy` (Auto / Manual / KeepOnFailure) + `ResourceScope` (Self / Parent / Root) [engine], `ResourceClosePolicy` (auto / manual / keepOnFailure / parent / parentKeepOnFailure / run / runKeepOnFailure) [notation-level] | kzen-lib-common `exec/engine/`, `exec/logic/` |
| Tracing (wire contract) | `LogicTrace` (lookup / lookupRun / lookupRunHistory / lookupRunExecutions / mostRecent / tracedLocations / clear / clearAll), `LogicTraceHandle` (set / append / clearAll / register — the Report write adapter, `ExecutionLogicTraceHandle`, still uses it over `Execution.emit`/`log`; its `register` / `clearAll` are no-ops there), `LogicTracePath` (+ the `$stable` and `$outcome` markers, the latter via `nodeOutcome(stableId)` — §7), `LogicTraceEntry` / `LogicTraceEvent` / `LogicTraceSnapshot` / `LogicTraceQuery`; REST entry point `LogicTraceEndpoint` + `LogicConventions` actions; engine-side `TraceEvent` (sequence, nodeId, stableId, address, value) + `Address` | kzen-lib-common `exec/logic/trace/`, `exec/engine/`; endpoint kzen-auto-jvm `server/objects/logic/` |
| Trace address routing (§7 SPI) | `LogicTraceAddressRouting` (`marker` → `tracePath(address, stableId)`), autowired and indexed by marker in `RunEngineLogicTrace`; contributed by `JobTraceAddressRouting` (`$job-progress`) and `ReportTraceAddressRouting` (`$trace-path`). Flavours emitting only element addresses (Script, Flow) contribute none | kzen-auto-jvm `server/exec/`, `server/exec/{job,report}/` |
| Trace values | `ExecutionValue` hierarchy (Null / Text / Boolean / Number / Long / **Binary** / **BinaryHandle** / List / Map). `BinaryHandleExecutionValue` (run + content hash + size + mime) is the **wire-only** substitution for a `BinaryExecutionValue` (§7), applied at `RunEngineLogicTrace.toWireValue` and resolved by `lookupBinary` behind the `/logic/trace-binary` blob route | kzen-lib-common `exec/`; substitution kzen-auto-jvm `server/exec/` |
| Trace store (**now: the engine itself**) | No separate store. The `LogicTrace` REST surface is served by projecting the **retained `RunEngine`** at query time (`RunEngineLogicTrace`, reached via `ServerLogicController.retainedTraceAccess`): the node tree yields the execution tree / `mostRecent` / `tracedLocations`; per-node `live` (+ `liveSequence`) yields `lookup` / `lookupRun` (whole-run merge keeps the latest node per stable id + latest sequence, reproducing the former re-entry clearing); each terminal node also yields a synthesized `nodeOutcome` entry (§7); `history` (log events) yields `lookupRunHistory`. Each flavour's within-node `Address` → wire `LogicTracePath` translation is applied at query time, not write time (the routing SPI row above). Rename survival: paths stay `ObjectStableId`-keyed, resolved to the current location via `ObjectStableMapper`, dropped when the object is deleted. `clear(objectLocation)` is degenerate — it acts as the global clear only when the location is the retained run's root, and the client uses `clearAll`. (Retired: the bridged in-memory `LogicTraceStore` + `ServerLogicController.mirrorTrace` / `onFrameClosed` / `onTraceReset`.) | view kzen-auto-jvm `server/exec/`; engine kzen-lib-jvm |
| Interactive request/response | `ExecutionRequest` / `ExecutionResult` / `RequestParams`; `Run.request` / `LogicController.request`; `Execution.onRequest`. Job's external duplex bridge (root-node router → serving Worker by channel name, timeout-capped) is in `JobRun.route`; Report registers its preview handler directly | kzen-lib-common `exec/`; bridge kzen-auto-jvm `server/exec/{job,report}/` |
| Flavour extensibility (§8) | `LogicDocument.toLogic` — implemented by a runnable document's `main` archetype; `LogicCompiler.compile` resolves it polymorphically from a `filterTransitive`-narrowed graph (no flavour `when`), and is the same entry point a nested host compiles its child through; `LogicCompilerServices` is the bundle every compiler threads | kzen-auto-jvm `server/exec/` |
| Example consumers (illustrative only) | `ScriptLogic` / `ScriptRunContext` / `ScriptLogicCompiler`, `FlowLogic` / `FlowRun`, `JobLogic` / `JobRun` / `WorkerLogic` / `EngineJobControl` / `JobDeadlockMonitor` (the flavour-owned liveness detector, §2), `ReportLogic` / `ReportRun`; client driver `ClientLogicGlobal` (SSE push + adaptive poll + client-paced auto-step) | kzen-auto-jvm `server/exec/**`, `server/objects/**`; kzen-auto-js `client/service/logic/` |

Related reading: [`architecture.md`](architecture.md) § "Execution model (Logic / Task / Trace)" and
§ "Stable identity"; the Job plan `kzen/plans/2026-07-25_job-improvements.md` (the most worked-out
concurrent + migrating consumer; its appendices consolidate the typed `JobMessage` element model and
the retired 2026-06-23 build plan);
`kzen-auto/docs/architecture.md` §1/§3 (Script as the reference Logic and
the REST run-control surface).
