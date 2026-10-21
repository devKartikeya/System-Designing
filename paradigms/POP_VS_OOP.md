# Procedural vs Object-Oriented Programming

## 1. Procedural Programming (POP)

Procedural programming organizes a program around procedures or functions that operate on data.

### Key characteristics
- Functions define the operations.
- Execution follows an explicit sequence of steps.
- Data and operations are commonly organized separately.
- Suitable for straightforward, computation-focused tasks.

### Example
A calculator with `add()`, `subtract()`, and `multiply()` functions.

## 2. Object-Oriented Programming (OOP)

Object-oriented programming organizes a program around objects that combine behavior with a representation of state.

### Key characteristics
- Objects encapsulate state and behavior.
- Abstraction exposes contracts while hiding implementation details.
- Polymorphism allows different implementations to be used through a common contract.
- Composition and inheritance can help model relationships when used appropriately.

### Example
A calculator that executes different implementations of a `MathOperation` interface.

## 3. Key Differences

| Aspect | Procedural | Object-Oriented |
|---|---|---|
| Primary organization | Functions and procedures | Objects and their behavior |
| Data and behavior | Often separate | Can be encapsulated together |
| Extending behavior | Add or modify procedures | Add implementations or modify existing code where necessary |
| Typical strength | Simple, direct computations | Systems with evolving behavior and domain models |
| Main risk | Scattered logic as complexity grows | Unnecessary abstractions and overengineering |

## 4. Engineering Trade-offs

Neither paradigm is universally superior.

Procedural code can be simpler and easier to maintain for small, stable computations. Object-oriented design can help when behavior varies, responsibilities need clear boundaries, or implementations must be interchangeable.

The choice should depend on the problem's complexity, expected change, testing needs, and team conventions—not on the assumption that OOP is always more advanced.