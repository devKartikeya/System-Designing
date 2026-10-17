# Clean Code

Clean code is code whose **cost of change stays low** as the system, the team, and the requirements grow. Aesthetics are a side effect; the real objective is economic: minimize the time required to understand intent, predict the blast radius of a modification, and verify that nothing else broke.

Every guideline below is a heuristic with a cost model attached. None of them is a law. When a rule and the needs of the system disagree, the system wins, but you should be able to articulate *why* in terms of change, failure, and comprehension cost.

> Examples are in TypeScript where types carry part of the argument. The principles are language-agnostic.

---

## 1. Naming

A name is the smallest unit of design. It compresses a concept into a handle that every reader will use for the lifetime of the code, so its quality compounds.

### Name the domain concept, not the mechanism

```ts
// Describes mechanics; the reader must reverse-engineer the intent
const list = users.filter((u) => u.f === 1 && u.d < cutoff);

// Describes intent in the language of the domain
const delinquentAccounts = accounts.filter(
  (account) => account.isPastDue(cutoff),
);
```

Adopt the **ubiquitous language** of the domain. If the business says "settlement", the code says `settlement`, not `payoutRecord`, `txFinal`, or `completedTransfer`. Divergence between the code's vocabulary and the domain's vocabulary is a reliable predictor of misunderstanding between engineers and stakeholders.

### Encode semantics that the type system cannot

Primitives carry no units, no invariants, no provenance. When a mix-up is costly, make it impossible rather than documented.

```ts
// Ambiguous: seconds? milliseconds? minor units or major?
function charge(amount: number, timeout: number) {}

// Cheap: encode in the identifier
function charge(amountMinor: number, timeoutMs: number) {}

// Strong: encode in the type
type Minor = number & { readonly __unit: "minor" };
type Major = number & { readonly __unit: "major" };

function charge(amount: Minor, timeoutMs: number) {}
```

### Conventions that pay for themselves

- **Scope-proportional length.** `i` is fine in a three-line loop. A module-level binding or exported symbol needs a name that survives being read without its surrounding context.
- **Predicates read as assertions:** `isExpired`, `hasPermission`, `canRetry`. Avoid negated names (`isNotEnabled`); they invert under `!` and produce double negatives.
- **Consistent verb semantics.** Choose a vocabulary and hold it: `get`/`find` for cheap in-memory lookups, `fetch`/`load` for I/O, `compute`/`derive` for non-trivial work, `ensure`/`assert` for invariants that throw. A reader should infer cost and failure modes from the verb alone.
- **No type-echoing or noise words.** `userObject`, `dataList`, `AccountManager`, `ProcessHelper`: the suffix is a signal that the responsibility was never defined.
- **Constants.** `UPPER_SNAKE_CASE` is a convention for truly static, module-level values (`MAX_LOGIN_ATTEMPTS`). A `const` binding of a computed or local value is not a constant in this sense.

Renaming is the cheapest and most underused refactoring. If a name has stopped reflecting the concept, change it now; the cost grows with every new call site.

---

## 2. Functions and Methods

### One level of abstraction per function

A function should read as a sequence of steps at a single level of abstraction. Mixing policy ("what must happen") with mechanism ("how bytes are parsed") forces the reader to context-switch continuously.

```ts
async function registerUser(input: unknown): Promise<User> {
  const command = parseRegistration(input);        // boundary validation, pure
  await assertEmailAvailable(command.email);       // I/O, may throw
  const user = User.register(command, clock.now());// domain logic, pure
  await userRepository.save(user);                 // I/O
  await outbox.enqueue(new UserRegistered(user.id)); // see note below
  return user;
}
```

The orchestrating function states *policy*; each callee hides *mechanism*. Note the outbox: publishing directly to a broker after `save` is a dual write and fails in the gap between the two. Writing the event in the same transaction and relaying it asynchronously is what makes the sequence reliable.

### Depth over length

Function length is a weak proxy. The better question is whether the interface is **narrow relative to the functionality it hides** (Ousterhout's *deep modules*).

- A function that wraps a single call and adds a name but no semantics is a *shallow* abstraction: it adds indirection and a navigation hop without reducing complexity.
- Extract when a block has a **nameable responsibility, an independent reason to change, or an independent test**, not when it crosses an arbitrary line count.
- Inline when an extraction obscures a linear flow that was easier to read in one place.

### Parameters are a statement about missing concepts

- **Boolean flag parameters** (`render(page, true)`) are two functions disguised as one, and the call site is unreadable. Split them or use a named option.
- **Parameter clumps** that travel together (`start, end`, `lat, lng`, `host, port, tls`) are an undiscovered type. Introduce it.
- **Positional arguments beyond two or three** hurt more than they help; use a parameter object so the call site is self-documenting and argument order stops being load-bearing.

```ts
// Opaque call site
scheduleJob(job, 3, 500, true, false);

// Self-describing
scheduleJob(job, { retries: 3, backoffMs: 500, jitter: true, persistent: false });
```

### Make contracts explicit

Prefer explicit inputs and predictable outputs. When a function has preconditions, enforce them at the boundary and keep the interior free of defensive checks. Validate once at the edge, then pass **parsed, trusted values** inward ("parse, don't validate").

---

## 3. Comments and Documentation

Code expresses *what* and *how*. It cannot express **why this and not the obvious alternative**, **which external constraint forced this shape**, or **which invariant must survive future edits**. Those are the comments worth writing.

```ts
// Webhooks are delivered at-least-once and may arrive out of order.
// Deduplicate on event.id and compare `created` against the stored version
// before applying; otherwise a delayed `invoice.updated` overwrites newer
// state. See ADR-014.
async function applyBillingEvent(event: BillingEvent) {
  // ...
}
```

### What to document

- **Non-obvious decisions and rejected alternatives**: link to an ADR or ticket instead of re-deriving the argument in prose.
- **Invariants and preconditions** that the type system cannot encode.
- **Contracts of public interfaces**: failure modes, idempotency, ordering and thread-safety guarantees, complexity when it matters.
- **Workarounds**, with a pointer to the upstream issue and the condition under which they can be removed.

### What not to document

- Restatements of the implementation (`// increment retry count`).
- Change history (`// fixed in v2`): version control owns this.
- Unowned TODOs. A TODO without a ticket, owner, or trigger condition is a permanent fixture. Write `// TODO(PLAT-2291): remove once clients are on v3`.

### Comments decay

A comment is an unverified claim that nothing enforces. Treat staleness as a defect and prefer mechanisms that fail loudly: a type, an assertion, a test whose name states the invariant. When a comment is compensating for confusing code, fix the code.

---

## 4. Error Handling

### Distinguish the two kinds of failure

| | **Operational errors** | **Programmer errors** |
|:--|:--|:--|
| **Examples** | Timeout, validation failure, 404, lock contention | `TypeError`, violated invariant, impossible branch |
| **Nature** | Expected in a correct program | A defect |
| **Response** | Handle, retry, translate, or report | Fail fast; surface loudly; let the supervisor restart |
| **Anti-pattern** | Treating as exceptional crashes | Catching and continuing in a corrupted state |

Conflating the two is the root of most bad error-handling code: either bugs get swallowed as if they were recoverable, or routine conditions bring down the process.

### Handle at the layer that can decide

An error should be caught by the first layer that has **enough context to make a meaningful decision**: retry, degrade, compensate, or report. Below that layer, let it propagate; do not catch merely to log.

```ts
// Anti-pattern: log-and-rethrow at every layer produces N log lines
// per failure with no added information.
try {
  await saveUser(user);
} catch (error) {
  logger.error("Failed to save user", { error });
  throw error;
}
```

Log **once, at the boundary that handles the error**, with correlation identifiers. Intermediate layers should add context by wrapping, not by logging.

### Preserve causality across boundaries

Translate errors at architectural boundaries so that lower-level details do not leak upward, but never discard the original.

```ts
class PaymentUnavailableError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = "PaymentUnavailableError";
  }
}

async function charge(order: Order) {
  try {
    return await gateway.charge(order.total, order.paymentMethod);
  } catch (cause) {
    if (isTimeout(cause)) {
      throw new PaymentUnavailableError(
        `Gateway timeout charging order ${order.id}`,
        { cause },
      );
    }
    throw cause; // unknown: do not guess, do not mask
  }
}
```

At the outermost boundary (HTTP handler, queue consumer), map error types to responses. Return a stable, minimal message and a correlation id to the caller; keep stack traces, paths, query text, and secrets in logs only.

### Model expected failures in the type system

For failures that are part of a function's normal contract, an exception hides the possibility from the signature. A discriminated result forces callers to confront it.

```ts
type Result<T, E> = { ok: true; value: T } | { ok: false; error: E };

function parseQuantity(raw: string): Result<number, "not_a_number" | "out_of_range"> {
  const n = Number(raw);
  if (!Number.isFinite(n)) return { ok: false, error: "not_a_number" };
  if (n < 1 || n > 100) return { ok: false, error: "out_of_range" };
  return { ok: true, value: n };
}
```

### Retries are a design decision

Retry only **idempotent** operations (or ones made idempotent with a key), with **exponential backoff and jitter**, a bounded **budget**, and awareness of the downstream's capacity. Unbounded or synchronized retries convert a partial outage into a full one.

**Catching an error is not handling it. Handling means the system is in a known-good state afterwards, and someone who needs to know has been told.**

---

## 5. Code Organization

Organization should answer one question cheaply: *where does a change of this kind belong, and what else must change with it?*

### Optimize for cohesion and directional dependencies

- **Things that change together live together.** Group by *reason to change* (feature, bounded context), not by technical layer. A `billing/` module that contains its routes, domain logic, and persistence beats `controllers/`, `services/`, `models/` spanning every feature.
- **Dependencies point toward stability.** Domain logic must not import infrastructure; infrastructure depends on domain abstractions. If your pricing rules import an HTTP client, the architecture is inverted.
- **Narrow public surfaces.** A module exports a deliberate API and hides the rest. Deep imports into another module's internals (`import … from "../billing/internal/ledger"`) turn private structure into a de facto contract.

```text
src/
  billing/
    index.ts            # public API: the only entry point other modules may use
    invoice.ts          # domain
    pricing.ts          # domain (pure)
    invoice-repo.ts     # infrastructure adapter
    http.ts             # delivery
  identity/
    index.ts
    ...
```

### Enforce boundaries mechanically

Conventions that rely on code review erode. Encode them: lint rules for restricted imports (`eslint-plugin-boundaries`, `no-restricted-imports`), dependency analysis (`dependency-cruiser`, `madge`) in CI to forbid **cycles** and illegal layer crossings.

### Watch for structural pathologies

- **Cyclic dependencies** make modules impossible to understand, test, or extract in isolation.
- **Barrel files** (`index.ts` re-exporting everything) are fine as a deliberate public API but can defeat tree-shaking and create hidden cycles when used indiscriminately.
- **"Utils" and "common"** are dumping grounds. They accrete unrelated functions with unrelated reasons to change, and everything ends up depending on them.

Both extremes fail: a god-module that every change touches, and a hundred micro-modules where understanding a flow requires opening twenty files.

---

## 6. Readability and Consistency

Readability is the cost of **building an accurate mental model** from the text. It is reduced by lowering cognitive load, not line count.

### Reduce cognitive complexity

- **Guard clauses and early returns** flatten nesting and keep the happy path at the left margin.
- **Limit the state a reader must hold.** Every mutable variable, flag, and branch condition is something to track; fewer is better.
- **Prefer locality of behavior.** A reader should be able to understand a unit without chasing indirection across files, unless the indirection earns its keep.

```ts
// Deep nesting: the reader must hold every enclosing condition in mind
function shippingCost(order: Order) {
  if (order) {
    if (order.items.length > 0) {
      if (!order.isDigital) {
        return computeCost(order);
      }
    }
  }
  return 0;
}

// Guard clauses: each line is a self-contained fact
function shippingCost(order: Order) {
  if (order.items.length === 0) return 0;
  if (order.isDigital) return 0;
  return computeCost(order);
}
```

### Make illegal states unrepresentable

The strongest readability tool is a model in which invalid combinations cannot be constructed. This eliminates whole categories of defensive code and tests.

```ts
// Permits nonsensical states: success with an error, loading with data
interface RequestState {
  status: "idle" | "loading" | "success" | "error";
  data?: Payload;
  error?: Error;
}

// Each state carries exactly the data that is valid for it
type RequestState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; data: Payload }
  | { status: "error"; error: Error };

function render(state: RequestState) {
  switch (state.status) {
    case "idle":    return null;
    case "loading": return spinner();
    case "success": return view(state.data);
    case "error":   return failure(state.error);
    default: {
      const unreachable: never = state; // compile error if a variant is added and unhandled
      throw new Error(`Unhandled state: ${JSON.stringify(unreachable)}`);
    }
  }
}
```

### Consistency is a force multiplier

A consistent codebase lets readers pattern-match instead of reason. Automate what can be automated (formatter, linter, import ordering, commit conventions) so that review attention is spent on design and correctness. Prefer the established convention over a locally "better" one; the cost of inconsistency is paid by every future reader.

### Prefer clarity over cleverness

Dense one-liners, implicit coercions, and operator tricks save keystrokes once and cost comprehension on every read. Optimize for the reader at 2 a.m. during an incident.

---

## 7. Code Smells

A smell is a **surface symptom correlated with elevated cost of change**. It is evidence worth investigating, not a verdict. Diagnose the underlying force, then decide.

| Smell | What it usually indicates | Typical response |
|:--|:--|:--|
| **Divergent change** | One module changes for many unrelated reasons (low cohesion) | Split by reason to change |
| **Shotgun surgery** | One logical change forces edits in many places (a concept is scattered) | Consolidate the concept behind one module |
| **Feature envy** | Behavior lives far from the data it manipulates | Move the behavior to the data's owner |
| **Primitive obsession / stringly typed** | A domain concept is encoded as raw strings or numbers | Introduce a value type with its invariants |
| **Data clumps** | The same group of fields travels together everywhere | Extract a type |
| **Temporal coupling** | Calls must occur in a hidden order (`init()` then `run()`) | Make the order structural (constructor, builder, typestate) |
| **Boolean blindness / flag args** | A function secretly has multiple modes | Split into separate functions or use named variants |
| **Speculative generality** | Hooks, options, and layers for requirements that do not exist | Delete until a second real use case appears |
| **Shared mutable state** | Behavior depends on who touched the data last | Localize, make immutable, or serialize access |

### Think in terms of connascence

Connascence classifies *how* two components are coupled (by name, type, position, meaning, algorithm, timing). The useful rule: **convert strong forms into weaker ones, and keep strong coupling within the smallest possible boundary.** Positional-argument coupling across a module boundary is worse than the same coupling inside one function.

### Let data pick the targets

Smells are everywhere; refactoring budget is finite. Prioritize **hotspots**: code that is both complex *and* frequently changed (churn × complexity, derived from version control). Ugly code that never changes is cheap. Moderately ugly code that changes weekly is expensive.

---

## 8. Refactoring

Refactoring is the disciplined restructuring of code **without changing observable behavior**, in steps small enough to verify. Its purpose is to reduce the cost of the *next* change: *"make the change easy, then make the easy change."*

### Operating discipline

- **Two hats.** Either you are changing behavior or you are restructuring, never both in the same step. Keep them in separate commits so each is reviewable and revertible.
- **Safety net first.** Without tests that pin current behavior, you are editing blind. For legacy code, write **characterization tests** (or golden-master tests) that capture what the system *does*, not what it should do, before touching it.
- **Chesterton's fence.** Before removing code or a check that looks pointless, find out why it exists. `git blame`, the linked ticket, and the original PR often explain a scar that looks like a bug.
- **Small, reversible steps** with the test suite green after each. A refactor you cannot bisect is a refactor you cannot trust.

### Strategies for changes that cannot be atomic

- **Parallel change (expand → migrate → contract):** add the new interface alongside the old, migrate callers incrementally, remove the old one. Applies equally to function signatures, database schemas, and APIs.
- **Branch by abstraction:** introduce a seam, implement the replacement behind it, switch over (optionally behind a flag), delete the old path.
- **Strangler fig:** route increasing slices of traffic or functionality to the new implementation until the legacy one can be retired.

### When not to refactor

Refactoring has a cost and a risk. Defer it when the code is stable and rarely touched, when you lack the means to verify behavior, or when the justification is taste rather than a concrete reduction in future cost. The test is simple: *which specific upcoming change becomes cheaper or safer?* If you cannot name one, the work probably is not justified yet.

---

## 9. Side Effects and State

Reasoning cost scales with the amount of state a unit can read or change that is **not visible in its signature**. Every hidden dependency is a coupling you cannot see in review.

### Command–query separation

A function should either **return information** (query) or **change state** (command), not both. Queries become safe to call, cache, reorder, and reason about; commands become the explicit, auditable locus of change. Violations (`pop()`-style operations aside) tend to produce bugs that depend on call order and call count.

### Functional core, imperative shell

Push decisions into **pure functions** over plain data; confine I/O and mutation to a thin outer layer that gathers inputs, calls the core, and applies the result. The core is trivially testable (no mocks), and the shell is small enough to cover with a few integration tests.

```ts
// Pure decision: no I/O, no clock, no globals
function decideRenewal(sub: Subscription, now: Date): RenewalDecision {
  if (sub.cancelledAt) return { kind: "skip", reason: "cancelled" };
  if (now < sub.renewsAt) return { kind: "skip", reason: "not_due" };
  return { kind: "charge", amountMinor: sub.plan.priceMinor };
}

// Imperative shell: gathers inputs, applies effects
async function renewDueSubscriptions(deps: Deps) {
  const now = deps.clock.now();
  for (const sub of await deps.subscriptions.findDue(now)) {
    const decision = decideRenewal(sub, now);
    if (decision.kind === "charge") {
      await deps.billing.charge(sub.id, decision.amountMinor, {
        idempotencyKey: `renewal:${sub.id}:${sub.renewsAt.toISOString()}`,
      });
    }
  }
}
```

### Treat nondeterminism as a dependency

Time, randomness, identifiers, environment, and the network are inputs. Reading them implicitly makes behavior unreproducible and tests flaky; injecting them makes both deterministic.

```ts
// Hidden dependency on the wall clock: untestable without fake timers
const isExpired = (token: Token) => Date.now() >= token.expiresAt;

// Explicit input
const isExpired = (token: Token, now: number) => now >= token.expiresAt;
```

### Mutation: be intentional, not dogmatic

Mutation is not the enemy; **unscoped, aliased, unannounced** mutation is. Prefer immutability at module boundaries and for shared data; allow local mutation inside a function when it is invisible to callers (an accumulator, a performance-critical loop). Be aware that spread and `Object.assign` copy **shallowly**: nested objects remain shared and are a classic source of aliasing bugs.

```ts
// Mutates the caller's object: effect is invisible at the call site
function applyDiscount(order: Order) {
  order.total *= 0.9;
}

// Returns a new value; the effect is the caller's explicit decision
function withDiscount(order: Order, rate: number): Order {
  return { ...order, total: order.total * (1 - rate) };
}
```

Also design effectful operations to be **idempotent** wherever the environment can duplicate them (retries, at-least-once delivery, user double-clicks). It converts a class of production incidents into non-events.

---

## 10. Abstraction and Complexity

An abstraction is a **bet that a boundary will remain stable while the things behind it change**. A good bet reduces complexity; a bad one adds a layer that must be understood, maintained, and eventually worked around.

### The wrong abstraction costs more than duplication

Duplication is a local, visible, cheap-to-fix cost. A premature abstraction is a global, invisible, expensive one: callers bend to fit it, flags and parameters accumulate to handle divergent cases, and removing it later requires untangling every user. This is why **"duplication is far cheaper than the wrong abstraction"** (Sandi Metz).

Practical heuristics:

- **Wait for the third instance** (rule of three) before generalizing; two examples rarely reveal the true axis of variation.
- Distinguish **incidental duplication** (code that looks alike today but changes for different reasons) from **essential duplication** (one piece of knowledge expressed twice, which must change together). Only the latter is a DRY violation. DRY is about *knowledge*, not text.
- If an abstraction's parameter list grows a new flag for each caller, it is being stretched over concepts that are not the same.

### Abstractions must add semantics

```ts
// Shallow: renames the ORM and adds nothing. Leaks its query language,
// so every caller still depends on the persistence technology.
class UserRepository {
  findOne(query: object) {
    return this.orm.findOne(query);
  }
}

// Deep: names a domain question and hides how it is answered.
interface Accounts {
  findDelinquentSince(date: Date): Promise<Account[]>;
  save(account: Account): Promise<void>;
}
```

The second can be backed by SQL, a cache, or an in-memory fake without callers noticing. The first changes nothing about what callers must know.

### Place seams where volatility and risk live

Introduce an interface at a boundary that is **genuinely likely to vary or must be substituted**: external services, clocks, persistence, anything slow or nondeterministic in tests. Do not wrap stable, internal, single-implementation code "for flexibility"; the indirection has a permanent comprehension cost and a speculative benefit.

### Every abstraction leaks

Under load, failure, or edge cases, implementation details surface (latency behind an "in-process" call that is actually remote; transaction semantics behind a "simple" repository). Design interfaces that **acknowledge the properties callers cannot ignore** (failure, latency, ordering, partial results) rather than pretending they do not exist.

**The measure is not the number of abstractions, but whether each one lowers the cost of understanding or changing the system more than it costs to maintain.**

---

## Review Checklist

Use these as prompts in design and code review, not as a pass/fail gate.

- Can I state what this unit does, and why it exists, without reading its callees?
- Does the name match the domain's vocabulary? Would the product owner recognize it?
- Which inputs does this really depend on: time, randomness, global state, environment?
- What are the failure modes, and which layer owns each? Who gets told?
- Can invalid states be constructed? If so, can the model forbid them?
- Which specific upcoming change does this abstraction make cheaper? What does it cost?
- If this needed to be removed or replaced next quarter, how many places would change?
- Is this complexity essential to the problem, or accidental to our solution?

---

## Final Thought

Clean code is not an aesthetic achievement or a checklist to follow blindly. It is the result of **repeated, deliberate trade-offs** made in favor of the people who will read, operate, and change the system after you.

The real measure of quality is whether an engineer who did not write it can understand its intent, predict its failure modes, change it safely, and trust the result, without having to fight complexity that did not need to exist.

Optimize for comprehension, reversibility, and the actual constraints of the system. Rules are a means; judgment is the skill.