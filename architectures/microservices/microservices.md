# Microservices Architecture

## 1. What Is Microservices Architecture?

Microservices architecture is an architectural approach in which an application is built as a collection of small, independently deployable services. Each service focuses on a specific business capability and communicates with other services through well-defined interfaces, commonly APIs or messaging systems.

Unlike a traditional monolithic application, where multiple modules run as part of one deployable unit, microservices can be developed, deployed, scaled, and maintained independently.

For example, an e-commerce platform might have separate services for:

- User Management
- Product Catalog
- Order Processing
- Payments
- Notifications

Each service owns a specific responsibility and can evolve independently, provided it maintains its agreed interfaces.

## 2. How It Works

A typical request flow might look like this:

1. A client sends a request to an API Gateway or an application endpoint.
2. The relevant service processes the request.
3. If additional work is required, the service communicates with another service using an API or asynchronous messaging.
4. The services perform their responsibilities and return results.
5. The application delivers the response to the client.

Not every request needs to pass through multiple services. Good designs minimize unnecessary inter-service communication.

## 3. Example Architecture

```text
                 Client
                   |
              API Gateway
                   |
        +----------+----------+
        |          |          |
   User Service  Order      Product
                 Service    Service
                    |
               Payment Service
                    |
             Payment Provider
```

This is an illustrative architecture, not a requirement. Real systems may use different communication patterns and service boundaries.

## 4. Key Characteristics

- **Independent deployment:** Services can be released separately.
- **Business-focused boundaries:** Each service owns a defined business capability.
- **Independent scaling:** Busy services can receive more resources without necessarily scaling every other service.
- **Technology flexibility:** Different services may use different technologies when justified.
- **Network-based communication:** Services communicate across process or machine boundaries.
- **Failure isolation:** A failure can sometimes be contained within one service, provided dependencies and failure handling are designed appropriately.

## 5. Advantages

- **Independent scaling:** Scale high-traffic services selectively.
- **Faster independent releases:** Teams can deploy services without releasing the entire application.
- **Team autonomy:** Different teams can own different services.
- **Fault containment:** Proper timeouts, retries, and isolation can prevent some failures from spreading.
- **Technology flexibility:** Services can choose suitable tools for their requirements.

## 6. Disadvantages

- **Operational complexity:** Multiple services require deployment, monitoring, logging, and configuration management.
- **Network failures:** Calls between services can fail, become slow, or time out.
- **Data consistency challenges:** Distributed transactions and cross-service workflows are more difficult to manage.
- **Debugging complexity:** A single user request may travel through several services.
- **Infrastructure costs:** Multiple deployments and supporting systems may increase cost.
- **Security overhead:** Service-to-service authentication, authorization, secrets, and network security require attention.

Microservices do not automatically make an application faster, more reliable, or easier to maintain.

## 7. Data Ownership and Communication

A common design principle is for each service to own its data and expose access through its interface rather than allowing other services to modify its database directly.

For example, the Order Service may request a payment through the Payment Service instead of directly writing to the Payment Service's database.

Services may communicate synchronously using HTTP APIs or asynchronously using message brokers and events.

However, separate databases for every service are not a universal requirement. Data ownership boundaries should be explicit, and the storage design should fit the system's needs.

## 8. Monolith vs. Microservices

| Aspect | Monolith | Microservices |
|---|---|---|
| Deployment | Usually one application unit | Multiple independently deployable services |
| Scaling | Often scales the whole application | Services can scale independently |
| Communication | Often direct in-process calls | Commonly network calls or messages |
| Data management | Often shared application database | Service-owned data is a common pattern |
| Operational complexity | Generally lower initially | Generally higher |
| Local development | Often simpler | May require multiple services and dependencies |
| Failure handling | Failures may affect the whole application | Failures can cross service boundaries unless contained |

Neither architecture is universally superior. The right choice depends on business requirements, team structure, traffic patterns, and operational maturity.

## 9. When Should You Use Microservices?

Consider microservices when:

- Different business capabilities have significantly different scaling needs.
- Multiple teams need independent ownership and release cycles.
- The application has clear domain boundaries.
- Independent deployment provides measurable business value.
- The organization can support distributed-system monitoring, security, and operations.

A modular monolith is often a better starting point for smaller applications, early-stage products, and teams that do not yet need independent service deployment.

## 10. Practical Example: Express.js Microservices Application

This repository includes a simple Express.js application in [`microservices-app/`](../microservices-app/) that demonstrates the fundamental concepts of microservices architecture.

The application consists of two independently running services:

- **Users Service:** Runs on port `3001` and exposes HTTP endpoints for retrieving user information.
- **Payment Service:** Runs on port `3002` and exposes an endpoint for demonstrating payment processing.

### Project Structure

```text
microservices-app/
├── users-service/
│   ├── package.json
│   └── server.js
├── payment-service/
│   ├── package.json
│   └── server.js
└── README.md
```

### How It Works

1. The Users Service exposes endpoints to retrieve user information.
2. The Payment Service receives a payment request.
3. The Payment Service sends an HTTP request to the Users Service to verify that the requested user exists.
4. If the user is found, the Payment Service returns a simulated successful payment response.

### Why Is This a Microservices Example?

Unlike the monolithic example, where Users and Payment are modules within one Express.js application, these services run as separate application processes.

Each service has its own entry point, port, and package configuration. The Payment Service communicates with the Users Service through an HTTP API rather than directly calling its internal functions.

This demonstrates two fundamental concepts:

- **Independent runtime:** Each service runs as a separate process.
- **Service-to-service communication:** Services communicate through network-based APIs.

### Important Limitations

This is an educational example, not a production-ready payment system. User data is stored in memory, payment processing is simulated, and the service URL is configured directly in the code. The services are independently runnable, but production-grade independent deployment would also require appropriate deployment configuration and operational tooling.

### Explore the Implementation

- [Microservices Application](./microservices-app/)
- [Users Service](./microservices-app/users-service/)
- [Payment Service](./microservices-app/payment-service/)

**Key takeaway:** Microservices are distinguished by independently running services and communication across service boundaries—not simply by placing code in separate folders.

## 11. Key Takeaway

Microservices are a way to organize an application around independently deployable services, not simply a way to split code into multiple folders.

Their benefits come with real distributed-systems costs. Choose them when independent ownership, deployment, or scaling solves a demonstrated problem—not merely because the architecture appears more advanced.

**Engineering principle:** Prefer the simplest architecture that satisfies current requirements, and introduce distributed complexity when its benefits justify its costs.