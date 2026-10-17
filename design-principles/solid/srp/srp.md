[← Design Principles](../design-principles.md) · Next: [Open/Closed Principle →](ocp.md)

# Single Responsibility Principle (SRP)

> *A module should be responsible to one, and only one, actor.* (Robert C. Martin, refined from the original "one reason to change")

SRP is a statement about **who causes change**, not about how many things a class does. Its purpose is to keep unrelated change requests from colliding inside the same unit of code, where a fix for one stakeholder silently breaks another.

> Examples are in TypeScript. The principle applies at every scale: function, class, module, package, service.

## What It Is Not

The popular paraphrase, *"a class should do one thing"*, is a useful **function-level** heuristic and a misleading **design-level** principle. Two consequences:

- A class with fifteen methods can satisfy SRP if all of them serve the same actor and change for the same reasons.
- A class with a single method can violate it if that method interleaves rules owned by different stakeholders.

A **reason to change** is a request from a person or group. An **actor** is the set of stakeholders who would ask for the same kind of change. Responsibility, in this sense, is *accountability to an actor*. It is not a unit of work.

## Anatomy of a Violation

The canonical example: one `Employee` class serving three actors.

```ts
class Employee {
  constructor(
    readonly id: string,
    readonly timesheet: Timesheet,
    readonly rate: Money,
  ) {}

  calculatePay(): Money {            // specified by Finance
    return this.rate.times(this.regularHours() + 1.5 * this.overtimeHours());
  }

  reportHours(): HoursReport {       // specified by HR
    return { employeeId: this.id, hours: this.regularHours() };
  }

  save(): Promise<void> {            // specified by Platform / DBAs
    return db.upsert("employees", this);
  }

  // Shared helper: the hidden coupling between Finance and HR
  private regularHours(): number {
    return Math.min(this.timesheet.totalHours(), 40);
  }
}
```

The failure mode is not aesthetic; it is an incident:

1. Finance asks for a change to how overtime is bounded and edits `regularHours()`.
2. The change is correct for payroll and passes payroll's tests.
3. HR's weekly report, which shares the helper, changes silently. Nobody who requested the change knows the report depends on it, and nobody who owns the report was consulted.

Shared code between actors is **coupling that crosses organizational boundaries**. The `private` modifier hides it from callers and from reviewers.

## Separating by Actor

```ts
interface EmployeeData {              // plain data: no policy, no persistence
  readonly id: string;
  readonly timesheet: Timesheet;
  readonly rate: Money;
}

class PayCalculator {                 // owned by Finance's requirements
  calculate(e: EmployeeData): Money {
    const regular = Math.min(e.timesheet.totalHours(), 40);
    const overtime = Math.max(e.timesheet.totalHours() - 40, 0);
    return e.rate.times(regular + 1.5 * overtime);
  }
}

class HoursReporter {                 // owned by HR's requirements
  report(e: EmployeeData): HoursReport {
    return { employeeId: e.id, hours: Math.min(e.timesheet.totalHours(), 40) };
  }
}

class EmployeeRepository {            // owned by Platform's requirements
  save(e: EmployeeData): Promise<void> { /* ... */ }
}
```

Each class now has exactly one answer to *"who can ask me to change?"*

### On the duplicated rule

The `40` appears twice, deliberately. The question is whether it is **one piece of knowledge** or **two that happen to agree**:

- If Finance and HR must *by definition* use the same number, that is a single rule. Extract it into an explicitly owned module (`workWeek.regularHours(timesheet)`) so that changing it is a visible, cross-team decision.
- If they merely coincide today and may diverge, duplication is the correct price of independence. This is DRY applied to *knowledge*, not text (see [Design Principles](../design-principles.md#dry--dont-repeat-yourself)).

### Reassembling for convenience

If callers want one entry point, provide a thin facade that **delegates and contains no policy**. The facade is allowed to depend on all three; none of them depends on it.

## Detecting Violations

| Signal | What it suggests |
|:--|:--|
| **Divergent change:** one file edited by unrelated PRs, different teams, for different reasons | Multiple actors share the module |
| Frequent **merge conflicts** in the same file between unrelated features | Same |
| A module imports SQL, HTTP, PDF, and domain rules together | Persistence, transport, presentation, and policy are fused |
| Describing the unit requires "and" or "also" | Several reasons to change |
| Tests need unrelated fixtures (a database *and* a PDF renderer) to check one rule | Responsibilities are entangled |
| Names like `Manager`, `Handler`, `Processor`, `Utils` | The responsibility was never defined |

Version control is the most honest detector: `git log --follow` on a suspect file shows *why* it changes. If the commit messages read like different people's backlogs, you have found multiple actors.

## Scale

- **Function:** one level of abstraction, one reason to change (see [Clean Code §2](../clean-code.md#2-functions-and-methods)).
- **Class / module:** one actor. Cohesion is the structural expression of SRP.
- **Package / service:** the *Common Closure Principle*: group things that change for the same reason and at the same time; separate things that do not.
- **Team:** by Conway's law, module boundaries tend to mirror team boundaries. SRP at the module level is partly an organizational design decision.

## Misapplications

**Splitting by method count or by step.** Decomposing a single-actor workflow into `UserNameValidator`, `UserNameNormalizer`, `UserNameFormatter`, `UserNamePolicy` produces *shallow* classes: each adds a name, an import, and a navigation hop, and none hides meaningful complexity. The cost of understanding rises while no change-collision was prevented.

**Splitting before actors diverge.** If one stakeholder owns the whole behavior, one cohesive class is correct. Wait for evidence (divergent change, a second actor appearing) before introducing a seam. A premature split is a speculative abstraction and carries the same costs as any other (see YAGNI).

**Confusing orchestration with responsibility.** A use-case class that sequences collaborators (`validate → save → publish`) has one responsibility: *coordinating this use case*. Its dependence on several collaborators does not make it a violation; mixing the collaborators' *logic* into it would.

| Situation | Split? |
|:--|:--|
| Two actors, shared helper, incidents from cross-effects | **Yes**, separate by actor |
| One actor, many cohesive methods | No |
| One method, interleaving rules from different stakeholders | **Yes** |
| A long function, one actor, linear flow | Probably extract by *abstraction level*, not by SRP |
| Speculation that "marketing might want this someday" | No (YAGNI) |

## Relationship to Other Principles

- **High cohesion / low coupling:** SRP is cohesion defined by *reason to change*.
- **[Open/Closed](ocp.md):** a module with a single reason to change is far easier to close against that change.
- **[Interface Segregation](isp.md):** the same idea on the consumer side. SRP stops a class serving many actors; ISP stops a client depending on capabilities for actors it is not.
- **Separation of Concerns:** SRP is the *organizational* criterion for choosing which concerns to separate.

## Review Questions

- Who would ask me to change this? If the answer names more than one stakeholder group, why are they in one module?
- If the most recent three changes to this file had different authors and different motivations, what coupled them?
- If I split this, which *specific* collision does the split prevent? If I cannot name one, do not split.
- Is shared code here one piece of knowledge or two coincident ones?

---

Next: [Open/Closed Principle →](ocp.md)