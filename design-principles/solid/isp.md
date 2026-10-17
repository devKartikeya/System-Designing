[← Open/Closed](ocp.md) · [Design Principles](../design-principles.md) · Next: [Interface Segregation Principle →](isp.md)

# Liskov Substitution Principle (LSP)

> *If S is a subtype of T, then objects of type T may be replaced with objects of type S without altering any of the desirable properties of the program.* (Barbara Liskov, 1987; formalized with Jeannette Wing as *behavioral subtyping*, 1994)

LSP is the principle that makes polymorphism **safe**. Type signatures only promise that a call *compiles*; LSP demands that the call still **means the same thing**. A subtype that satisfies the compiler but changes the behavior callers rely on is not a subtype in any sense that matters. It is a latent bug waiting for the right caller.

> Examples are in TypeScript. The principle applies to any interface contract: classes, duck-typed protocols, plugins, drivers, and versioned APIs.

## Subtyping Is About Behavior

The test is not "does `S` have all of `T`'s methods?" but **"can any code written against `T`'s contract run correctly against `S`, unchanged, without knowing `S` exists?"**

If client code must ask *which* implementation it received (`instanceof`, a type tag, a capability flag) before using it safely, substitutability has failed and the abstraction is leaking.

## The Contract Rules

Design by Contract (see [Design Principles](../design-principles.md#design-by-contract)) gives LSP its precise form. A subtype may adjust the contract only in directions that cannot surprise a caller:

| Contract element | Subtype may | Subtype must not | Intuition |
|:--|:--|:--|:--|
| **Preconditions** (what callers must supply) | **Weaken** (accept more) | Strengthen (demand more) | Callers only satisfy `T`'s requirements |
| **Postconditions** (what callee guarantees) | **Strengthen** (promise more) | Weaken (promise less) | Callers rely on `T`'s guarantees |
| **Invariants** | Preserve (may add) | Break | State must stay valid across all operations |
| **History constraint** | Add behavior consistent with `T` | Permit state transitions `T` forbids (e.g. mutating something `T` defines as immutable) | Callers reason about how state evolves |
| **Exceptions / failures** | Throw those in `T`'s contract (or their subtypes) | Introduce new failure modes callers do not handle | Error handling is part of the contract |
| **Return types** | Narrower (covariant) | Wider | Callers get at least what `T` promised |
| **Parameter types** | Wider (contravariant) | Narrower | Callers may pass anything `T` accepted |

## Canonical Violation: Rectangle and Square

```ts
class Rectangle {
  constructor(protected w: number, protected h: number) {}
  setWidth(w: number)  { this.w = w; }
  setHeight(h: number) { this.h = h; }
  area() { return this.w * this.h; }
}

class Square extends Rectangle {
  constructor(side: number) { super(side, side); }
  setWidth(w: number)  { this.w = w; this.h = w; }  // maintains "square" invariant
  setHeight(h: number) { this.w = h; this.h = h; }
}

function resize(r: Rectangle) {
  r.setWidth(5);
  r.setHeight(4);
  console.assert(r.area() === 20);   // holds for Rectangle, FAILS for Square (16)
}
```

`Rectangle` carries an implicit postcondition: *`setWidth` changes the width and leaves the height alone*. `Square` weakens it. A square *is-a* rectangle in geometry, but **mutable `Square` is not substitutable for mutable `Rectangle` in behavior.** Taxonomy in the problem domain does not determine subtyping in code.

Resolutions, in order of preference:

- Make the shapes **immutable values** (`withWidth` returns a new shape). The coupled-mutation problem disappears.
- Model by **behavior, not taxonomy**: a `Shape` with `area()`, and independent `Rectangle` and `Square` implementations.
- If mutation is essential, do not make one extend the other.

## A Realistic Violation: The Read-Only Implementation

```ts
interface BlobStore {
  put(key: string, data: Uint8Array): Promise<void>;
  get(key: string): Promise<Uint8Array>;
}

class ReadOnlyBlobStore implements BlobStore {
  async put(): Promise<void> {
    throw new Error("store is read-only");   // strengthens the precondition to "never callable"
  }
  async get(key: string) { /* ... */ }
}
```

Any client that persists data now fails at runtime with a failure mode the `BlobStore` contract never listed. The type checker approved it; production will not.

The fix is to correct the **abstraction**, not patch the subtype. See [ISP](isp.md):

```ts
interface BlobReader { get(key: string): Promise<Uint8Array> }
interface BlobWriter { put(key: string, data: Uint8Array): Promise<void> }
interface BlobStore extends BlobReader, BlobWriter {}

class ReadOnlyBlobStore implements BlobReader { /* ... */ }
// Code that needs to write now *cannot be handed* a read-only store: the compiler enforces it.
```

The pattern generalizes: when a subtype "cannot support" an inherited operation, the hierarchy encoded a capability that not every member has. Split the capability.

## Enforcing LSP: Contract Tests

Types cannot express behavioral contracts. **A shared contract test suite**, executed against every implementation, is the practical enforcement mechanism.

```ts
export function blobStoreContract(name: string, create: () => Promise<BlobStore>) {
  describe(`${name} satisfies the BlobStore contract`, () => {
    it("returns exactly what was stored", async () => {
      const store = await create();
      await store.put("k", bytes("v1"));
      expect(await store.get("k")).toEqual(bytes("v1"));
    });

    it("overwrites on repeated put", async () => {
      const store = await create();
      await store.put("k", bytes("v1"));
      await store.put("k", bytes("v2"));
      expect(await store.get("k")).toEqual(bytes("v2"));
    });

    it("fails with NotFoundError, not null or a generic Error, for a missing key", async () => {
      const store = await create();
      await expect(store.get("missing")).rejects.toBeInstanceOf(NotFoundError);
    });
  });
}

blobStoreContract("InMemoryBlobStore", async () => new InMemoryBlobStore());
blobStoreContract("S3BlobStore",       async () => new S3BlobStore(testBucket()));
```

A new implementation is "done" when it passes the suite. This also turns the *implicit* contract (missing key semantics, overwrite behavior, error types) into an explicit, reviewable artifact.

## Detecting Violations

| Signal | What it indicates |
|:--|:--|
| `instanceof` / type-tag / capability checks in client code | Clients cannot treat implementations uniformly |
| Overrides that throw `NotImplemented`, `UnsupportedOperationException`, or silently do nothing | Subtype refuses part of the contract |
| Documentation like "do not call `x()` on the `Foo` implementation" | Contract exceptions encoded in prose |
| Subtype adds validation the base class does not (`if (!(arg instanceof Special)) throw`) | Strengthened precondition |
| Subtype returns `null`/empty where base guarantees a value | Weakened postcondition |
| Same-named method with different semantics (`equals`/`compareTo` inconsistent with each other; `close()` that does not release) | Behavioral drift |
| Implementation-specific tests required to "make it work" in generic code | Contract is under-specified |

## TypeScript-Specific Cautions

- **Structural typing proves less than it seems.** Any object with matching members satisfies the interface, whether or not it honors the behavior. Signature compatibility is necessary and nowhere near sufficient.
- **Method parameters are bivariant**, even under `strict`. The `strictFunctionTypes` flag applies to function-typed *properties*, not method declarations. A subtype method accepting a *narrower* parameter type than the base compiles, violating contravariance. Prefer property-style function types where soundness matters, and rely on contract tests.
- **Async and error contracts are invisible to the compiler.** A method that resolves to `null` instead of rejecting, or rejects with a new error class, passes type checking.

## Beyond Classes

LSP governs every place where one thing stands in for another:

- **Database drivers and storage backends** behind a common interface (does `SELECT ... FOR UPDATE` behave the same on SQLite?).
- **Plugins and strategies:** an extension that requires special handling in the host.
- **Mocks and fakes:** a fake with looser semantics than the real service hides production failures.
- **Versioned APIs:** a `v2` endpoint that is "compatible" but changes ordering, pagination defaults, or error shapes violates substitutability for existing clients.

## Relationship to Other Principles

- **[OCP](ocp.md):** extension through polymorphism is only legitimate if extensions are substitutable. A subtype requiring client special-casing means the module is not truly closed.
- **[ISP](isp.md):** the standard cure for "this subtype can't do X": narrower interfaces so no one promises what they cannot deliver.
- **Composition over inheritance:** inheritance maximizes LSP exposure, since a subclass inherits *every* behavior and assumption of its parent. Composition lets you expose only what you can honor.
- **Design by Contract:** LSP is its formal consequence for subtyping.

## Review Questions

- What does the base type promise beyond its signatures: ordering, idempotency, failure types, mutation semantics?
- Does every implementation honor all of it? Is there a contract test that proves it?
- Does any caller branch on the concrete type, or catch an exception only one implementation throws?
- Is this "is-a" a statement about *behavior* callers can rely on, or only about the vocabulary of the domain?
- If an implementation cannot support an operation, is the right fix a different interface rather than a throwing stub?

---

[← Open/Closed](ocp.md) · Next: [Interface Segregation Principle →](isp.md)