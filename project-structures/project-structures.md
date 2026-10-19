# Project Structures

**Organizing Codebases for Maintainability, Scalability, and Change**

Project structure is not merely about arranging files into folders. It defines how developers discover code, how responsibilities are separated, and how easily a codebase can evolve as features and teams grow.

This section explores two common approaches to organizing application code:

- **Type-Based Structure** — Organizing files by their technical responsibility.
- **Feature-Based Structure** — Organizing files by the business capability or feature they belong to.

Neither approach is universally superior. The right choice depends on application complexity, team workflows, dependency boundaries, and how the codebase is expected to evolve.

---

## 1. Type-Based Structure

A type-based structure organizes files according to their technical role, such as controllers, services, models, routes, and middleware.

### Example

```text
src/
├── controllers/
│   ├── userController.js
│   └── productController.js
├── services/
│   ├── userService.js
│   └── productService.js
├── models/
│   ├── userModel.js
│   └── productModel.js
├── routes/
│   ├── userRoutes.js
│   └── productRoutes.js
├── middleware/
├── config/
└── app.js
```

### How It Works

Files serving the same technical purpose are grouped together, regardless of which feature they support.

For example, all controllers live in `controllers/`, while all business services live in `services/`.

### Advantages

- Simple to understand and establish in small applications.
- Provides clear separation by technical responsibility.
- Makes shared technical infrastructure easy to locate.
- Works well when the codebase has relatively few features.

### Limitations

As the number of features grows, a single feature's implementation can become scattered across many directories.

Adding or modifying a feature may require navigating between controllers, services, models, routes, validators, and tests. Large technical directories can also become difficult to navigate and may encourage unrelated features to depend on one another.

**Best suited for:** Small applications, prototypes, and codebases where the number of features and cross-cutting complexity remain manageable.

---

## 2. Feature-Based Structure

A feature-based structure organizes files around business capabilities. Each feature keeps its related components together instead of distributing them across global technical directories.

### Example

```text
src/
├── features/
│   ├── users/
│   │   ├── user.routes.js
│   │   ├── user.controller.js
│   │   ├── user.service.js
│   │   ├── user.model.js
│   │   └── user.test.js
│   └── products/
│       ├── product.routes.js
│       ├── product.controller.js
│       ├── product.service.js
│       ├── product.model.js
│       └── product.test.js
├── shared/
│   ├── middleware/
│   ├── errors/
│   └── utils/
├── config/
└── app.js
```

### How It Works

Each feature owns the files needed to implement its behavior. The users feature contains its routing, controller, service, model, and tests; the products feature does the same.

Common infrastructure that genuinely serves multiple features can live in `shared/` or another appropriate application-level module.

### Advantages

- Keeps related code close together.
- Makes feature development and debugging easier.
- Helps teams work on different business capabilities independently.
- Makes it easier to understand a feature's implementation in one place.
- Can support clearer module boundaries as the application grows.

### Limitations

Feature-based organization does not automatically guarantee modularity. Features can still become tightly coupled through imports, shared mutable state, or direct access to each other's internals.

Shared code can also become a dumping ground if boundaries are not defined carefully. Small features may acquire unnecessary folder and file overhead.

**Best suited for:** Applications with multiple business capabilities, growing codebases, and teams that benefit from clear feature ownership.

---

## Type-Based vs Feature-Based

| Dimension | Type-Based | Feature-Based |
|---|---|---|
| Organization | Technical responsibility | Business capability |
| Related feature files | Often spread across directories | Usually colocated |
| Finding a complete feature | May require navigating multiple folders | Generally more direct |
| Small applications | Often simple and sufficient | Can introduce unnecessary structure |
| Growing applications | Technical directories can become crowded | Can improve feature-level organization |
| Team ownership | Often aligned by technical layer | Can align with business capabilities |
| Modularity | Depends on dependency design | Depends on dependency design |
| Main risk | Scattered feature logic | Poorly defined feature boundaries |

The key difference is **how code is grouped**, not whether the code is inherently clean, scalable, or maintainable.

---

## 3. How to Choose

Consider the application you are building rather than selecting a structure based on popularity.

Choose type-based organization when:

- The application is small and has few independent features.
- Most developers need to navigate the same small set of technical layers.
- Additional modular boundaries would add more overhead than value.

Choose feature-based organization when:

- The application has multiple distinct business capabilities.
- Features change at different rates.
- Developers frequently modify several files belonging to one feature.
- Clear ownership and controlled dependencies are becoming important.

For a larger application, a **hybrid structure** can also be effective: organize the application by feature, then organize each substantial feature internally by technical responsibility.

---

## 4. Hybrid Structure

```text
src/
├── features/
│   ├── users/
│   │   ├── controllers/
│   │   ├── services/
│   │   ├── models/
│   │   ├── routes/
│   │   └── tests/
│   └── billing/
│       ├── controllers/
│       ├── services/
│       ├── models/
│       ├── routes/
│       └── tests/
├── shared/
│   ├── middleware/
│   ├── errors/
│   └── utils/
├── config/
└── app.js
```

This combines feature-level discoverability with technical separation inside each feature.

However, the hybrid approach is not automatically the best option. Creating many nested directories for tiny features can make a project harder to navigate. Introduce structure in proportion to actual complexity.

---

## 5. Structure Is Not Architecture

A critical distinction: **folder organization does not determine the application's architecture by itself.**

- A monolithic application can use a feature-based structure.
- A monolithic application can use a type-based structure.
- A microservices system can organize each service using either approach.
- A well-organized folder tree can still contain tightly coupled, untestable code.

Architecture concerns runtime boundaries, deployment, communication, data ownership, and system behavior. Project structure concerns how the codebase is organized and navigated.

Good project organization can support a sound architecture, but it cannot replace sound architectural decisions.

---

## Engineering Principles

When designing a project structure:

1. **Optimize for change.** Keep code that changes together reasonably close together.
2. **Make dependencies explicit.** A folder boundary is useful only when dependencies respect the intended boundary.
3. **Keep shared code intentional.** Share stable, genuinely reusable behavior rather than creating a universal utilities directory.
4. **Avoid premature abstraction.** Start with the simplest structure that supports the current requirements.
5. **Refactor based on friction.** Reorganize when navigation, testing, ownership, or change isolation becomes difficult.
6. **Preserve testability.** Structure code so that important behavior can be tested without unnecessary infrastructure.

## Final Thought

A good project structure reduces the cognitive effort required to understand and change a codebase.

The goal is not to maximize the number of folders or enforce a universal convention. It is to make responsibilities discoverable, dependencies understandable, and future changes safer.

**Choose the structure that fits the way the application changes—not simply the way its files look today.**

---

**Author:** [Kartikeya Mishra](https://github.com/devKartikeya)  
**Repository:** [System-Designing](https://github.com/devKartikeya/System-Designing)