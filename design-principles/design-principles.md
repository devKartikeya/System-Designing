# Design Principles

Design principles are compressed engineering experience: heuristics for managing complexity, keeping change affordable, and making behavior predictable. Each one encodes a **trade-off** that was paid for repeatedly in production systems before it became a slogan.

They are not laws. A principle is only valuable in the context that produced it: the problem, the constraints, the team, and the expected rate and direction of change. Applied mechanically, every one of them degrades into its own anti-pattern. Applied with judgment, they give you a shared vocabulary for arguing about trade-offs precisely instead of by taste.

> Examples are in TypeScript where types carry part of the argument. The principles are language-agnostic.

## Map of the Principles

| Principle | The question it answers |
|:--|:--|
| [DRY](#dry--dont-repeat-yourself) | Is each piece of *knowledge* expressed in exactly one place? |
| [KISS](#kiss--keep-it-simple) | Is this the least intertwined solution that meets the real requirements? |
| [YAGNI](#yagni--you-arent-gonna-need-it) | Am I paying carrying cost for a need that does not exist yet? |
| [Premature Optimization](#premature-optimization) | Do I have measurements, or only intuition? |
| [Separation of Concerns](#separation-of-concerns) | Can each concern change without dragging the others along? |
| [High Cohesion, Low Coupling](#high-cohesion-low-coupling) | Do things that change together live together, and little else? |
| [Encapsulation / Information Hiding](#encapsulation-and-information-hiding) | Which design decisions are hidden, and who is protected from them? |
| [Orthogonality](#orthogonality) | Does changing one thing leave unrelated things untouched? |
| [Law of Demeter](#law-of-demeter-principle-of-least-knowledge) | How much of other objects' structure does this code know? |
| [Tell, Don't Ask](#tell-dont-ask) | Does behavior live next to the data it governs? |
| [Composition over Inheritance](#composition-over-inheritance) | Am I reusing behavior through the most flexible mechanism? |
| [Fail Fast](#fail-fast) | Is a broken assumption detected where it breaks, or three layers later? |
| [Design by Contract](#design-by-contract) | What does each side of an interface promise and require? |
| [Least Astonishment](#principle-of-least-astonishment) | Does this behave the way a competent caller would predict? |
| [Robustness Principle](#robustness-principle-postels-law) | How tolerant should a boundary be, and at what long-term cost? |
| [Least Privilege / Defense in Depth](#least-privilege-and-defense-in-depth) | What is the blast radius when one component is compromised or wrong? |
| [Convention over Configuration](#convention-over-configuration) | Does the common case require zero decisions? |
| [Boy Scout Rule](#boy-scout-rule) | Does the code improve as a side effect of normal work? |
| [SOLID](#solid) | Responsibilities, extension, substitutability, interfaces, dependencies |

---

## DRY — Don't Repeat Yourself

> *Every piece of knowledge must have a single, unambiguous, authoritative representation within a system.* (Hunt & Thomas)

DRY is about **knowledge**, not text. The unit of analysis is a *decision or rule* (a pricing policy, a validation constraint, a schema), not a sequence of characters. Two identical blocks encoding different decisions are not a violation; two different-looking blocks encoding one decision are.

### Duplicated knowledge drifts

When a rule lives in several places, the copies diverge under change, and the inconsistency surfaces as a production bug, not a compile error.

```ts
// One policy, restated at two call sites. The next requirement change
// ("also require MFA enrollment") will update one and forget the other.
const canResetPassword = (u: User) => u.isActive && u.emailVerified;
const canPerformSensitiveAction = (u: User) => u.isActive && u.emailVerified;

// One named concept, one authority
const isEligibleForSensitiveOperations = (u: User) =>
  u.isActive && u.emailVerified;
```

### Derive; don't restate

The most expensive DRY violations are **cross-layer**: the same constraint independently written in the database schema, the API validator, the client form, and the documentation. Make one the source of truth and generate the rest.

```ts
const CreateUserSchema = z.object({
  email: z.string().email(),
  displayName: z.string().min(2).max(40),
});
type CreateUser = z.infer<typeof CreateUserSchema>; // type derived from the schema

// Same idea at larger scale: OpenAPI → generated clients, SQL schema → generated types.
```

Likewise, **stored state that can be computed from other state is duplicated knowledge.** Persist the minimal set, derive the rest, and cache deliberately when you must (with an explicit invalidation story).

### Coincidental duplication is not duplication

```ts
const isValidUsername    = (s: string) => /^[a-z0-9-]{3,30}$/.test(s);
const isValidProjectSlug = (s: string) => /^[a-z0-9-]{3,30}$/.test(s);
```

These match today for unrelated reasons. Merge them and the first divergence (product wants dots in usernames) forces a flag into a shared function, then another, until the abstraction is a conditional maze that every caller depends on. Duplicating is cheap, local, and reversible; **the wrong abstraction is expensive, global, and sticky.**

### Heuristics

- **Rule of three** (or AHA, *Avoid Hasty Abstractions*): tolerate duplication until the third instance reveals the real axis of variation.
- Ask: *"If this requirement changes, must all of these change together?"* Yes means one piece of knowledge, so unify. No means incidental similarity, so leave it.
- Prefer duplicating a few lines over coupling two modules that otherwise have no reason to know each other.

---

## KISS — Keep It Simple

Brooks separated **essential complexity** (inherent in the problem) from **accidental complexity** (introduced by our solution). KISS is the discipline of refusing to add accidental complexity.

Rich Hickey's distinction sharpens it: *simple* is an objective property (**not intertwined**: one role, one concept, one braid), while *easy* is a subjective one (**near at hand**, familiar). The familiar framework, the library you already know, and the pattern you used last time are easy but not necessarily simple. Choosing easy and calling it simple is how systems accumulate unexamined complexity.

```ts
// Accidental complexity: registry, dynamic dispatch, and indirection
// for two fixed channels that will not change soon
class NotifierRegistry { /* register(), resolve(), middleware chain... */ }
registry.register("email", new EmailNotifier());
registry.register("push", new PushNotifier());
await registry.dispatch(user, message);

// Simple: the shape of the code is the shape of the requirement
async function notify(user: User, message: string) {
  await sendEmail(user.email, message);
  if (user.pushToken) await sendPush(user.pushToken, message);
}
```

When a third and fourth channel with per-user routing rules arrive, introduce the abstraction then, with real variation to inform its shape.

### What simplicity is not

It does not mean skipping security, observability, data integrity, or known scale and compliance constraints; those are essential complexity, and omitting them is merely deferred cost. It also does not mean fewer lines: a longer, flatter, explicit function is often *simpler* than a dense, clever one.

**Rule of thumb:** every component, layer, and option must justify its *ongoing* cost (comprehension, testing, operation, failure modes) against a *present* benefit. A low-traffic service does not need a load balancer, a message bus, and a service mesh because those are what large systems use.

---

## YAGNI — You Aren't Gonna Need It

Do not build capabilities for a presumed future. Speculative features carry a **cost of carry** (they must be read, tested, maintained, secured, and worked around) while their expected benefit is discounted by the probability that the need never materializes, or materializes in a different shape than guessed. Speculation is usually wrong about the *shape*, so you pay the cost and still rewrite.

For example, building a configurable multi-tenant custom-domain system into a URL shortener before any customer asks for it adds routing, TLS provisioning, ownership verification, and abuse surface that must all be maintained for a feature with zero users.

### YAGNI governs features, not changeability

Fowler's framing: YAGNI applies to *presumptive capabilities*, not to the effort of keeping software easy to modify. Clean structure, tests, and small modules are what make deferral safe. Deferral is only cheap if change is cheap.

### Where YAGNI does not apply: expensive-to-reverse decisions

Weigh **cost of delay** and **cost of reversal** against **cost of carry**. Invest early only where reversal is disproportionately costly:

| Defer (cheap to change later) | Decide with care now (expensive to reverse) |
|:--|:--|
| Internal module structure | Public API contracts and versioning strategy |
| Configuration knobs, plugin points | Persisted data formats and identifier schemes |
| Optional features, extra integrations | Security boundaries, tenancy isolation model |
| Caching, performance tuning (once measured) | Data ownership between services |

Use the **one-way door / two-way door** heuristic: move fast through reversible decisions; slow down for irreversible ones.

**Rule of thumb:** implement when there is a concrete need; *shape* the system so that when the need arrives, it is a local change.

---

## Premature Optimization

Knuth's observation, in context: most small-scale efficiency tinkering is wasted effort and harms maintainability, but the critical few percent of code that dominates cost deserves serious attention. The failure is optimizing **without measurement**, not optimizing.

The corollary is Kent Beck's sequence: **make it work, make it right, make it fast**, in that order, with profiling (not intuition) deciding where "fast" is spent.

What *is not* premature: decisions that fix the system's asymptotic and structural performance envelope and are costly to change later. An O(n²) algorithm on unbounded input, an N+1 query pattern baked into an API shape, a data model that cannot be partitioned. Those are design, not micro-optimization.

**Rule of thumb:** choose sound algorithms and data models up front; defer constant-factor tuning until a profile or SLO violation names the bottleneck.

---

## Separation of Concerns

Partition a system so that **each concern is addressed in one place and can vary independently** of the others (Dijkstra). Concerns include transport, validation, authorization, business rules, persistence, presentation, and cross-cutting aspects like logging and tracing.

```ts
// Tangled: HTTP parsing, authorization, business rule, SQL, and response shape
// in one handler. Any change to any concern touches this function.
app.post("/transfer", async (req, res) => {
  const { from, to, amount } = req.body;
  if (!req.user) return res.status(401).end();
  const rows = await db.query("SELECT balance FROM accounts WHERE id = $1", [from]);
  if (rows[0].balance < amount) return res.status(422).json({ error: "insufficient" });
  await db.query("UPDATE accounts SET balance = balance - $1 WHERE id = $2", [amount, from]);
  // ...
});

// Separated: each layer has one reason to change
//   transport:    parse and validate input, map outcome → status code
//   application:  orchestrate a use case, own the transaction boundary
//   domain:       enforce the rule (a transfer may not overdraw)
//   persistence:  translate to and from storage
app.post("/transfer", authenticate, async (req, res) => {
  const cmd = parseTransfer(req.body);
  const result = await transferFunds(cmd, req.user, deps);
  res.status(statusFor(result)).json(present(result));
});
```

### Caveats

- **Separate along real axes of change**, not by technical layer for its own sake. Horizontal layering across every feature (`controllers/`, `services/`, `repositories/`) often scatters one feature across six directories; vertical slices (feature modules containing their own layers) frequently give better locality.
- Every boundary has a cost: mapping code, indirection, and another place to look. A seam that never sees independent variation is pure overhead.
- **Cross-cutting concerns** (auth, tracing, retries, metrics) resist clean separation. Middleware, decorators, and aspect-style mechanisms are the usual answers; use them deliberately, since they also hide control flow.

---

## High Cohesion, Low Coupling

The pair underlies most other principles.

- **Cohesion:** how strongly the elements *within* a module belong together. Functional cohesion (everything contributes to one well-defined purpose) is the target; coincidental cohesion (a `utils` file) is the failure.
- **Coupling:** how much modules must know about, and change together with, one another. Count not just direct imports but shared assumptions: data formats, ordering, timing, global state, a database table two services both write.

The practical test is one question about change: *when this requirement changes, how many modules must I open?* High cohesion and low coupling means the answer is one.

### Direction matters, not just amount

Not all coupling is equal. Coupling to something **stable and abstract** is benign; coupling to something **volatile and concrete** is expensive. Martin's *instability metric* `I = Ce / (Ca + Ce)` (efferent over total couplings) formalizes the intuition behind the **Stable Dependencies Principle**: depend in the direction of stability. Business rules should not depend on a web framework; the framework should depend on (or be adapted to) the rules.

### Design for deletion

A useful corollary: *write code that is easy to delete, not code that is easy to extend.* Modules with narrow interfaces and no hidden dependents can be replaced or removed wholesale. Extensibility mechanisms that thread through the system make removal impossible and are usually the source of the legacy you will later fear.

---

## Encapsulation and Information Hiding

Parnas's original criterion for decomposition: **a module hides a design decision that is likely to change.** Encapsulation is not about `private` keywords or getters and setters; exposing every field through an accessor hides nothing. It is about **protecting invariants** and **confining the impact of change**.

```ts
// Anyone can construct nonsense: negative amounts, mismatched currencies added together
interface Money { amount: number; currency: string }
const total = { amount: a.amount + b.amount, currency: a.currency }; // silently wrong

// The type owns its invariants; invalid states cannot be constructed or combined
class Money {
  private constructor(
    private readonly minor: number,
    readonly currency: Currency,
  ) {}

  static of(minor: number, currency: Currency): Money {
    if (!Number.isSafeInteger(minor)) throw new RangeError("minor units must be a safe integer");
    return new Money(minor, currency);
  }

  add(other: Money): Money {
    if (other.currency !== this.currency) {
      throw new CurrencyMismatchError(this.currency, other.currency);
    }
    return new Money(this.minor + other.minor, this.currency);
  }
}
```

The representation (integer minor units today, `bigint` or a decimal library tomorrow) can change without touching a single caller. That is the payoff: a **narrow interface in front of a decision that may change.**

**Failure mode:** a leaky interface that exposes internals (returning mutable internal collections, ORM entities across module boundaries, database column names in API responses) converts private structure into a de facto public contract.

---

## Orthogonality

Two components are **orthogonal** if changing one has no effect on the other. Orthogonal systems are easier to test (isolated), easier to reason about (local effects), and safer to change (small blast radius).

Tests for orthogonality:

- If I change the requirements for X, how many modules change? (The answer should be one.)
- Can I replace the database, the queue, or the auth provider without editing business logic?
- Does a function's behavior depend on global state, call order, or who else touched shared data?

Violations are typically *hidden*: global singletons, shared mutable caches, implicit initialization order, helpers that read environment variables, and modules that quietly share a table. The cure is making dependencies explicit (parameters, constructor injection) and narrowing the surface of shared state.

---

## Law of Demeter (Principle of Least Knowledge)

A unit should talk to its **immediate collaborators**, not to the collaborators' collaborators. The point is to limit how much of the surrounding object graph's *structure* your code is coupled to.

```ts
// The caller is coupled to Order → Customer → Address → Country.
// Any restructuring of those types breaks this line, and every line like it.
const country = order.getCustomer().getAddress().getCountry();

// Ask the collaborator for what you need; it owns how to find it
const country = order.shippingCountry();
```

### It is not about counting dots

Chained calls on the *same abstraction* are fine and often desirable: fluent builders, `items.filter(...).map(...)`, query builders, and pipelines all operate on one type. The principle targets **navigating through the internals of other objects**, i.e. structural coupling. Likewise, traversing a *pure data structure* (a parsed JSON payload, a DTO) is not a violation: its structure is its contract.

**Symptom to watch for:** "train wrecks" of getters (`a.getB().getC().getD()`), and tests that require building an entire object graph just to exercise one behavior.

---

## Tell, Don't Ask

Rather than pulling state out of an object to decide what to do with it, **tell the object what you want** and let it apply the rules. This keeps behavior adjacent to the data and invariants it governs, and prevents the same decision logic from being re-implemented by every caller.

```ts
// Ask: the caller reaches in, decides, and mutates. Every caller must replicate
// the overdraft rule, and one of them will get it wrong.
if (account.balance >= amount && !account.isFrozen) {
  account.balance -= amount;
}

// Tell: the rule lives with the data
account.withdraw(amount); // throws or returns a failure if the rule is violated
```

### Limits

- It is a guide for **objects that own invariants**, not a mandate to push every operation into a class. In a functional style, the equivalent is a pure function that takes the aggregate and returns the next state; the principle (keep the rule in one place) still holds.
- Overapplied, it produces god objects that absorb UI, persistence, and reporting concerns. Behavior belongs with the data **only when it is about that data's own invariants**.
- Queries are legitimate; the smell is *decisions made externally on exposed internals* (an anemic domain model with all logic in "services").

---

## Composition over Inheritance

Inheritance couples subclass to superclass **at compile time, through every protected member, and through the superclass's implementation** (the fragile base class problem). Composition assembles behavior from collaborators behind interfaces, and can be rearranged at runtime.

```ts
// Inheritance for reuse produces a combinatorial hierarchy:
// RetryingClient, TimeoutClient, RetryingTimeoutClient, CachingRetryingTimeoutClient...
class RetryingTimeoutFetchClient extends FetchClient { /* ... */ }

// Composition: each behavior is a small decorator over the same interface
interface HttpClient { get(url: string): Promise<Response> }

class RetryingClient implements HttpClient {
  constructor(private inner: HttpClient, private policy: RetryPolicy) {}
  get(url: string) { return retry(() => this.inner.get(url), this.policy); }
}

class TimeoutClient implements HttpClient {
  constructor(private inner: HttpClient, private ms: number) {}
  get(url: string) { return withTimeout(this.inner.get(url), this.ms); }
}

const client = new TimeoutClient(
  new RetryingClient(new FetchClient(), retryPolicy),
  2_000,
);
```

Each concern is independently testable and orderable, and new combinations require no new classes.

### When inheritance is still right

A genuine *is-a* relationship that satisfies behavioral substitutability (see LSP), a shallow hierarchy, and framework-defined extension points (template-method hooks) are legitimate. The guideline is a default, not a ban: **inherit to model a taxonomy; compose to reuse behavior.**

---

## Fail Fast

When an assumption is violated, **surface it immediately, at the point of violation, loudly.** A system that continues in an invalid state propagates corruption; by the time symptoms appear, the cause is several layers and several minutes away, and the evidence is gone.

```ts
// Misconfiguration discovered at 3 a.m. on the first request that touches it
const dbUrl = process.env.DATABASE_URL; // undefined, discovered much later

// Validated once at startup: a bad deploy fails the health check and never serves traffic
const Env = z.object({
  DATABASE_URL: z.string().url(),
  PORT: z.coerce.number().int().positive(),
});
export const env = Env.parse(process.env);
```

Applications of the principle:

- **Validate at boundaries** (config at boot, input at the edge) and pass trusted, parsed values inward.
- **Assert invariants** that must hold; treat violations as bugs, not recoverable conditions.
- **Prefer a crash with context over silent continuation** for programmer errors; let a supervisor restart a process rather than limp on with corrupted state.
- Avoid defaults that mask absence (`value || 0`, `?? ""`) when absence is actually an error.

**Distinction:** failing fast concerns *defects and invalid assumptions*. Expected operational failures (timeouts, 404s, rate limits) are handled, retried, or degraded gracefully, not crashed on.

---

## Design by Contract

Meyer's formulation treats an interface as a **contract** between caller and callee:

| Element | Responsibility | Meaning of violation |
|:--|:--|:--|
| **Precondition** | Caller | What must be true on entry. Violation is the *caller's* bug. |
| **Postcondition** | Callee | What is guaranteed on exit. Violation is the *callee's* bug. |
| **Invariant** | Both, always | What must hold before and after every public operation. |

```ts
function withdraw(account: Account, amountMinor: number): Account {
  // Preconditions: caller's obligation, checked at the boundary
  assert(Number.isSafeInteger(amountMinor) && amountMinor > 0, "amount must be a positive integer");
  assert(account.balanceMinor >= amountMinor, "insufficient funds");

  const next = { ...account, balanceMinor: account.balanceMinor - amountMinor };

  // Postcondition / invariant: callee's guarantee
  assert(next.balanceMinor >= 0, "invariant: balance never negative");
  return next;
}
```

The value is **clarity of blame and responsibility**: the interior of a function can assume its preconditions rather than defending against every possible input, and a violated contract points directly to the party at fault. Types, schemas, and runtime assertions are the practical enforcement tools; document the rest where the type system cannot express it.

---

## Principle of Least Astonishment

An API, function, or system should behave the way a reasonably experienced user would **predict from its name, signature, and surrounding conventions.** Surprise is a defect: it converts correct usage into incidents.

```ts
const sorted = items.sort(byPrice);   // Surprise: also mutated `items` in place
const sorted = items.toSorted(byPrice); // Names the non-mutating behavior (ES2023)
```

Sources of astonishment to hunt for:

- A `get*` or `is*` that performs I/O or mutates.
- Inconsistent argument order or naming across sibling functions.
- Defaults that are dangerous (`delete` that cascades silently, `force` as default).
- Behavior that depends on hidden global or ambient state.
- Return types that sometimes mean "not found", sometimes "error", sometimes "empty".

Astonishment is relative to the audience and the ecosystem: honor the conventions of the language, the framework, and the codebase over a locally "cleaner" idea.

---

## Robustness Principle (Postel's Law)

> *Be conservative in what you send, be liberal in what you accept.*

The first half is uncontroversial: emit strictly well-formed output. The second half has aged badly when read literally. Lenient parsing of malformed or ambiguous input produces **de facto behavior that later implementations must reproduce**, hardens quirks into the protocol (ossification), and is a recurring root cause of security issues, since differing leniencies across components become exploitable parsing discrepancies. The IETF's RFC 9413 revisits the principle for exactly these reasons.

A modern, defensible reading separates two things:

- **Tolerant reader (good):** ignore *unknown fields* and *additive changes* so producers can evolve without breaking consumers.
- **Lenient parsing of invalid input (risky):** guessing the meaning of malformed data. Reject it explicitly, with an error that says why.

```ts
// Tolerant of evolution: unknown fields are ignored
const Event = z.object({ id: z.string(), type: z.string() }).passthrough();

// Strict on meaning: invalid values are rejected, not coerced into something plausible
const Quantity = z.number().int().min(1).max(100);
```

**Rule of thumb:** be strict about *validity*, tolerant about *extension*.

---

## Least Privilege and Defense in Depth

Two complementary security-design principles that belong in the same toolbox as the structural ones, because they too are about **limiting blast radius**.

- **Least privilege:** every component, credential, and user gets the minimum access required for its job and nothing more. The application's database role should not be able to `DROP TABLE`; a background worker's token should not be able to read billing data; a service account scoped to one bucket should not see the others.
- **Defense in depth:** assume any single control can fail, and layer independent ones: input validation *and* parameterized queries *and* least-privilege DB roles *and* network segmentation *and* audit logging.
- **Secure by default:** the unconfigured path should be the safe one. Safe behavior should require no extra effort; dangerous behavior should require an explicit opt-in.

**Design question:** *if this component is compromised, or simply wrong, what is the worst thing it can do?* Architecture that makes that answer small is more valuable than any single hardening measure.

---

## Convention over Configuration

Provide **sensible defaults** so the common case needs no decisions, and reserve configuration for genuine variation. Each option is a decision a user must make, a combination you must test, and a failure mode you must document.

- Defaults should be **safe, boring, and correct for the majority** of cases.
- Keep **escape hatches** for the legitimate minority; convention without an override becomes a cage.
- Make convention **discoverable**: documented, enforced by tooling (generators, linters), and consistent across the codebase.

The trade-off: convention reduces decisions and onboarding cost at the price of implicit behavior ("magic"). It works when the conventions are few, stable, and well known; it fails when they are numerous, undocumented, or silently change between versions.

---

## Boy Scout Rule

> *Leave the code a little cleaner than you found it.*

Continuous, small, opportunistic improvement (a clearer name, an extracted function, a deleted dead branch, a missing test) prevents entropy more effectively than occasional large cleanups that never get scheduled.

Guardrails that keep it from becoming a liability:

- **Scope it to what you are already touching.** It is not a license for sweeping rewrites inside a feature change.
- **Separate commits for restructuring and behavior change**, so reviews stay legible and reverts stay safe.
- **Do not refactor what you cannot verify.** Without tests pinning the behavior, "cleanup" is an unreviewed behavior change.

---

## SOLID

SOLID is a group of five object-oriented design principles that help manage responsibilities, extension, substitutability, interfaces, and dependencies. Several of the principles above are the same forces viewed from a different angle (cohesion and SRP; encapsulation and ISP; coupling direction and DIP).

Each principle has its own document:

- [Single Responsibility Principle (SRP)](solid/srp.md)
- [Open/Closed Principle (OCP)](solid/ocp.md)
- [Liskov Substitution Principle (LSP)](solid/lsp.md)
- [Interface Segregation Principle (ISP)](solid/isp.md)
- [Dependency Inversion Principle (DIP)](solid/dip.md)

These principles guide design; applying them mechanically still produces overengineering.

---

## Applying Principles With Judgment

Principles pull against one another. Resolving the tension is the actual engineering work.

| Tension | Resolution |
|:--|:--|
| **DRY vs. KISS / Orthogonality** | A shared abstraction couples its callers. Unify only knowledge that shares a reason to change; otherwise accept duplication. |
| **KISS / YAGNI vs. Known Requirements** | Both reject *speculative* complexity, not *established* constraints (security, compliance, data integrity, known scale). |
| **YAGNI vs. Expensive-to-Reverse Decisions** | Defer reversible choices; invest early in public contracts, persisted formats, and trust boundaries. |
| **Separation of Concerns vs. Locality** | Every boundary adds indirection. Separate along real axes of change, not for symmetry. |
| **Encapsulation vs. Tell-Don't-Ask vs. Simplicity** | Rich objects protect invariants but can swell into god objects; balance with cohesion. |
| **Fail Fast vs. Resilience** | Crash on *defects*; degrade gracefully on *expected operational failure*. Know which is which. |
| **Postel vs. Fail Fast** | Tolerate additive evolution; reject invalid input explicitly. |
| **Composition vs. Simplicity** | Many small decorators and strategies can be harder to follow than one straightforward class. Compose when variation is real. |

### Decision heuristics

- **Cost of change is the common currency.** Prefer the design that makes the *likely next changes* cheapest, and that keeps *unlikely* ones possible, without optimizing for imagined ones.
- **Reversibility sets the level of rigor.** Fast and lightweight for two-way doors; deliberate and documented for one-way doors.
- **Let evidence, not aesthetics, trigger abstraction.** A second and third real instance, a measured bottleneck, or a recurring bug class justifies structure. A pattern name does not.
- **Mind the organization.** Systems tend to mirror the communication structure of the teams that build them (Conway's law). A design that fights team boundaries pays a coordination tax; align module boundaries with ownership where possible.
- **Name the trade-off.** If you cannot state what a principle costs in this specific case, you are probably applying it as dogma.

The objective is not to maximize the number of principles visibly applied. It is to make deliberate, defensible decisions that fit the system's real requirements, constraints, and rate of change, and to be able to explain *why* when someone asks.