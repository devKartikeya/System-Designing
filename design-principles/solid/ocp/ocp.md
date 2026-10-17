[← Single Responsibility](srp.md) · [Design Principles](../design-principles.md) · Next: [Liskov Substitution Principle →](lsp.md)

# Open/Closed Principle (OCP)

> *Software entities should be open for extension but closed for modification.* (Bertrand Meyer, 1988)

OCP is about **how a system absorbs a new requirement**. A well-structured module accepts new behavior by adding new code behind a stable seam, not by editing code that already works, has been reviewed, and is depended on. The payoff is containment: the blast radius of a feature is the new code, not the existing core.

> Examples are in TypeScript. The principle is language-agnostic.

## Two Historical Meanings

| | **Meyer (1988)** | **Martin (polymorphic OCP)** |
|:--|:--|:--|
| **Open** | Extend a class by *inheriting* from it | Extend by providing a new implementation of an *abstraction* |
| **Closed** | Existing source is frozen once published | Clients depend only on the stable abstraction |
| **Mechanism** | Implementation inheritance | Interfaces, polymorphism, composition |
| **Weakness** | Couples subclasses to superclass internals | Requires choosing the right abstraction |

The modern reading is the second: **depend on a stable abstraction, vary the implementations behind it.** Inheritance-as-extension is the weaker mechanism (see [Composition over Inheritance](../design-principles.md#composition-over-inheritance)).

## Anatomy of a Violation

```ts
function priceFor(order: Order): Money {
  switch (order.customer.tier) {
    case "standard":
      return order.subtotal;
    case "gold":
      return order.subtotal.percentOff(10);
    case "partner":
      return order.subtotal.percentOff(25).minus(order.partnerCredit);
    // Every new tier edits this function, its tests, and every OTHER
    // switch on `tier` scattered through the codebase.
  }
}
```

The symptom is **shotgun surgery**: one conceptual change (a new tier) demands coordinated edits in many places, and the compiler cannot tell you which ones you missed unless every switch is exhaustive.

## Closing Against the Axis of Change

```ts
interface PricingRule {
  appliesTo(order: Order): boolean;
  apply(price: Money, order: Order): Money;
}

class GoldTierDiscount implements PricingRule {
  appliesTo(order: Order) { return order.customer.tier === "gold"; }
  apply(price: Money) { return price.percentOff(10); }
}

class PartnerDiscount implements PricingRule {
  appliesTo(order: Order) { return order.customer.tier === "partner"; }
  apply(price: Money, order: Order) {
    return price.percentOff(25).minus(order.partnerCredit);
  }
}

class PricingEngine {
  constructor(private readonly rules: readonly PricingRule[]) {}

  priceFor(order: Order): Money {
    return this.rules
      .filter((rule) => rule.appliesTo(order))
      .reduce((price, rule) => rule.apply(price, order), order.subtotal);
  }
}

// Composition root: the one place that names concrete rules
const engine = new PricingEngine([new GoldTierDiscount(), new PartnerDiscount()]);
```

A new tier is a **new class plus one line of wiring**. `PricingEngine` and its tests are untouched.

Notes on what this design costs and implies:

- **Ordering becomes semantics.** Non-commutative rules (percentage then fixed credit vs. the reverse) make the sequence part of the contract. Make it explicit and test it.
- **The wiring is allowed to change.** Editing the composition root is expected; OCP concerns the *core*, not the place where parts are assembled.
- **The abstraction is a bet.** `appliesTo` / `apply` fits discount-like rules. A requirement such as "this rule needs the whole basket's history" may not fit, and then the abstraction itself must change.

## The Expression Problem: You Cannot Close Against Everything

Every structure is closed against some changes and open to others, and **closing one axis opens the other.**

| Representation | Adding a new **variant** | Adding a new **operation** |
|:--|:--|:--|
| **Polymorphic objects** (interface + implementations) | Easy: add a class | Hard: edit every implementation |
| **Sum type + functions** (discriminated union) | Hard: edit every switch | Easy: add a function |

```ts
type Shape =
  | { kind: "circle"; r: number }
  | { kind: "rect"; w: number; h: number };

const area = (s: Shape): number => {
  switch (s.kind) {
    case "circle": return Math.PI * s.r ** 2;
    case "rect":   return s.w * s.h;
    default: { const _exhaustive: never = s; return _exhaustive; }
  }
};

// New OPERATION: pure addition, no existing code modified
const perimeter = (s: Shape): number => { /* ... */ };

// New VARIANT: the compiler (via `never`) points at every switch to update
```

This is OCP along the *operations* axis and an explicit, compiler-checked trade along the *variants* axis. Neither is "more OCP"; the design decision is **which axis is expected to vary**. A closed, stable set of variants with many operations (an AST, a protocol's message types) favors sum types. An open set of variants behind a fixed operation set (payment providers, pricing rules, exporters) favors polymorphism.

## Strategic Closure and "Fool Me Once"

No design is closed against all change. Martin's practical rule: **closure must be strategic.** Guess which changes are likely, and shape the code for those.

The reliable method is empirical:

1. Write the first version simply (the plain `switch`).
2. When the **first change along that axis** arrives, you now have two data points and real knowledge of what varies.
3. Refactor to close against that axis, using what the change taught you.

Abstracting *before* the first change means designing the seam from a single example, which is how you get an interface that fits no real second case. This is YAGNI applied to extensibility, and it is why the [rule of three](../design-principles.md#dry--dont-repeat-yourself) exists.

## Detecting Violations

| Signal | Suggests |
|:--|:--|
| One feature request edits many existing files | The axis of change is not isolated (shotgun surgery) |
| The same `switch` or `if/else` chain on a type tag repeated in several modules | Missing polymorphism or missing shared abstraction |
| `instanceof` / type-tag checks in client code | Clients know concrete variants |
| Growing flag parameters or boolean config on a core function | Variation is being injected as conditionals instead of structure |
| New features routinely cause regressions in unrelated old ones | Existing code is the point of change |

## Misapplications

**An interface for every conditional.** A two-branch `if` that has never changed does not need a strategy hierarchy. Indirection has a permanent comprehension cost; the benefit is hypothetical until a second change arrives.

**"Closed" read as "never touch".** OCP does not forbid fixing bugs, nor changing a rule whose *requirements* changed. It addresses *adding* behavior without destabilizing existing behavior. If the rule itself is wrong, edit it.

**Plugin architecture for one plugin.** Registries, discovery mechanisms, and lifecycle hooks are machinery with maintenance cost. Build them when there is genuine third-party or multi-team extension, not for a single internal implementation.

**Configuration as extension.** Pushing variation into ever-larger configuration objects (`strategy: "a" | "b" | "c"` with nested options) relocates the `switch` rather than eliminating it, and loses type safety on the way.

## Relationship to Other Principles

- **[SRP](srp.md):** a module with one reason to change has a single axis along which it needs to be closed. Mixed responsibilities mean several competing axes.
- **[LSP](lsp.md):** OCP's polymorphism only works if new implementations are **substitutable**. If clients must special-case an extension, the module was never closed.
- **[DIP](dip.md):** closure is achieved by depending on abstractions owned by the stable core; DIP says where those abstractions live.
- **YAGNI:** governs *when* to introduce the seam. OCP governs *what shape* it takes.

## Review Questions

- What is the *specific* change this seam is meant to absorb? Has that kind of change already happened once?
- Which axis is likely to vary: new variants or new operations? Does the chosen representation make *that* axis cheap?
- Can a new implementation be added without editing the core, its tests, or any other consumer?
- Would a reviewer have to read the extension's code to know the core still behaves correctly? If so, the contract is too weak (see LSP).

---

[← Single Responsibility](srp.md) · Next: [Liskov Substitution Principle →](lsp.md)