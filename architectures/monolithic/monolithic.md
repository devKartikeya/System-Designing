# Monolithic Architecture

## 1. What Is Monolithic Architecture?

Monolithic architecture is a software design approach in which an application is built and deployed as a single, unified unit. Its components may have different responsibilities, but they typically run together as one application and often share the same codebase and deployment process.

For example, an e-commerce application might contain:
- User authentication
- Product management
- Shopping cart
- Order processing
- Payment processing

In a monolithic application, these modules can exist separately within the codebase while still being part of the same deployable application.

## 2. How It Works

A typical monolithic application follows this flow:

1. The client sends a request to the application.
2. The application routes the request to the appropriate module.
3. The module executes business logic and interacts with the database when needed.
4. The application returns a response to the client.

All these operations generally happen within one application runtime, although external services such as payment gateways or email providers may still be involved.

## 3. Common Structure

A monolithic application can be organized into logical layers:

```text
my-application/
├── controllers/
├── services/
├── repositories/
├── models/
├── routes/
├── config/
└── app.js
```

- **Controllers:** Handle incoming requests and responses.
- **Services:** Contain business logic.
- **Repositories:** Manage data access.
- **Models:** Represent application data.
- **Routes:** Define application endpoints.
- **Config:** Stores application configuration.

This is an example structure, not a mandatory convention.

## 4. Advantages

- **Simple deployment:** The application is deployed as one unit.
- **Easy initial development:** Developers can work within one codebase.
- **Straightforward debugging:** Requests can often be traced within a single application.
- **Efficient internal communication:** Modules can call each other directly without network requests.
- **Lower initial operational complexity:** Usually requires less infrastructure than a distributed system.

## 5. Disadvantages

- **Scaling limitations:** Scaling one module independently is difficult; the entire application may need additional resources.
- **Growing complexity:** A large codebase can become difficult to maintain without clear module boundaries.
- **Deployment risk:** A change to one module may require redeploying the whole application.
- **Shared resource contention:** CPU, memory, and database connections may be consumed by different modules.
- **Tightly coupled code:** Poorly designed modules can make changes risky and unpredictable.

These disadvantages are not inevitable. Good modular design can keep a monolith maintainable as it grows.

## 6. Monolithic Architecture vs. Modular Monolith

A **traditional monolith** may allow modules to depend heavily on one another.

A **modular monolith** keeps the application as one deployable unit but enforces clear boundaries between modules. Each module owns a specific responsibility and exposes well-defined interfaces to other modules.

A modular monolith can provide many benefits of good architectural separation without introducing the operational complexity of microservices.

## 7. When Should You Use It?

Monolithic architecture is often a good choice when:

- Building an MVP or a new product.
- Working with a small development team.
- Business requirements are still evolving.
- Independent scaling of modules is not yet necessary.
- Simplicity and fast iteration are important.

It may become less suitable when different parts of the system require independent deployment, scaling, or team ownership.

## 8. Practical Example: Express.js Monolithic Application

This repository includes a simple Express.js application in the [`monolithic-app/`](./monolithic-app/) directory to demonstrate monolithic architecture in practice.

The application contains two distinct modules:

- **Users:** Handles user-related functionality.
- **Payment:** Handles payment-related functionality.

The application also includes a `server.js` file that serves as the main entry point.

### Project Structure

```text
monolithic-app/
├── users/
│   └── ... user module files
├── payment/
│   └── ... payment module files
└── server.js
```

*Note: The structure above illustrates the main components; refer to the actual directory for the complete file structure.*

### Why Is This a Monolithic Application?

Although the Users and Payment modules are organized into separate folders, they belong to the same Express.js application.

- They are maintained within the same application codebase.
- The application starts through a common entry point, `server.js`.
- The modules are part of the same deployable application rather than independently deployed services.

This demonstrates an important concept: **separating code into folders does not automatically make an application microservices-based.**

The Users and Payment modules have separate responsibilities, but they remain components of one application.

### What Can You Learn From This Example?

Use this project to understand how a monolithic application can be organized into modules while retaining a single deployment unit.

As the application grows, clear module boundaries can help maintain readability and reduce unnecessary coupling without requiring an immediate move to microservices.

**Explore the implementation:** [`monolithic-app/`](./monolithic-app/)

## 9. Key Takeaway

Monolithic architecture is not inherently bad architecture. Its main characteristic is that the application is deployed as one unit, not that its code must be poorly organized.

The important engineering decision is to choose an architecture that fits the current requirements while keeping future change manageable.

**Principle:** Start with the simplest architecture that meets your needs, maintain clear module boundaries, and introduce distributed complexity only when there is a justified reason.