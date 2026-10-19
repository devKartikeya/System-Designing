# Load Balancing

## 1. Overview

Load balancing is the process of distributing incoming requests across multiple backend servers to improve availability, resource utilization, and throughput.

Instead of allowing all client traffic to reach a single application instance, a load balancer acts as an intermediary that selects an appropriate backend for each request.

A load balancer can operate at different network layers. This implementation focuses on **Layer 7 (Application Layer) HTTP load balancing**, where routing decisions are made while handling HTTP requests.

## 2. Why Load Balancing Matters

As an application grows, a single server may become a bottleneck because of limited CPU, memory, network capacity, or request-processing capacity.

Adding more application instances allows traffic to be distributed across multiple servers. However, simply running more instances is not sufficient: a system also needs a mechanism to route traffic, detect failures, and handle backend unavailability.

Load balancing provides this routing layer.

Key objectives include:

- Distributing incoming requests across available backends.
- Avoiding backends known to be unhealthy.
- Improving service availability when individual backend instances fail.
- Enabling horizontal scaling.
- Providing a centralized point for routing policies and request-level observability.

Load balancing does not automatically eliminate bottlenecks in shared databases, caches, or other dependencies.

## 3. Architecture

This repository contains a custom HTTP reverse proxy implemented in Python.

```text
                  Client
                    |
                    v
          +--------------------+
          |   Load Balancer    |
          |    Port: 8080      |
          |                    |
          |  Round Robin       |
          |  Health Checks     |
          +--------------------+
             /      |      \
            v       v       v
         App 1    App 2    App 3
         :9001    :9002    :9003
```

The load balancer receives an HTTP request, selects a healthy backend, forwards the request, and relays the backend's response to the client.

Health checks run separately to track backend availability.

## 4. Repository Implementation

Explore the implementation in [`/load-balancing/`](./).

```text
load-balancing/
├── load-balancer.md
├── load_balancer.py
├── config.json
├── backends/
│   └── app_server.py
```

### Core Components

**Load Balancer (`load_balancer.py`)**

Implements the HTTP reverse proxy, backend selection, health-check coordination, request forwarding, timeouts, and basic logging.

**Backend Pool**

Maintains backend instances and their health state. It selects a backend from the currently healthy set using round-robin scheduling.

**Health Checks**

Periodically send HTTP requests to each backend's health endpoint. Unsuccessful checks mark a backend unhealthy so it is excluded from subsequent selections.

**Backend Server (`backends/app_server.py`)**

Provides a simple HTTP application used to simulate multiple backend instances during local testing.

**Configuration (`config.json`)**

Defines the listener address, backend addresses, health-check path, and request timeout.

## 5. Load-Balancing Algorithm: Round Robin

Round Robin distributes requests sequentially across the available healthy backends.

For three healthy backends, the expected sequence is:

```text
Request 1  -> Backend 1
Request 2  -> Backend 2
Request 3  -> Backend 3
Request 4  -> Backend 1
Request 5  -> Backend 2
```

The implementation uses a thread-safe counter to select a position in the current healthy backend list.

### Complexity

For \(n\) configured backends, the current implementation filters the backend list before selecting an instance. Therefore, selection takes \(O(n)\) time, with \(O(n)\) temporary space for the filtered list.

A production implementation with a large backend pool could optimize selection using maintained healthy-backend state and an appropriate scheduling data structure.

### Trade-offs

Round Robin is simple, predictable, and easy to test. However, it does not account for:

- Different backend capacities.
- Current request concurrency.
- Request processing duration.
- Unequal workload costs.

For example, a backend handling several long-running requests may receive another request even when another backend is nearly idle.

Alternative algorithms include Weighted Round Robin, Least Connections, and latency-aware routing. Each requires additional state or assumptions.

## 6. Health Checks and Failure Handling

A load balancer should not continue routing requests to a backend known to be unavailable.

This implementation performs active HTTP health checks and excludes backends marked unhealthy.

When every backend is unhealthy, the proxy returns HTTP `503 Service Unavailable`.

When a selected backend fails during request forwarding, the proxy returns HTTP `502 Bad Gateway`.

These responses represent different situations:

- **503:** No healthy backend is currently available.
- **502:** A request to the selected backend failed.

### Current Limitation

The prototype marks a backend unhealthy after one failed health check. A transient failure can therefore remove a backend unnecessarily.

A more robust design would introduce:

- Configurable failure and recovery thresholds.
- Separate connection and response timeouts.
- Health-check intervals and jitter.
- Backend draining during planned shutdowns.
- Clear distinction between liveness and readiness checks.

## 7. Concurrency and Resource Management

The listener uses Python's `ThreadingHTTPServer`, allowing it to handle multiple requests concurrently.

However, unbounded thread creation can become a resource bottleneck under heavy traffic. A production design would require bounded concurrency, connection limits, backpressure, and appropriate worker management.

The current proxy also buffers complete request and response bodies in memory. This simplifies the implementation but increases memory pressure for large payloads.

Streaming request and response bodies would be an important improvement.

## 8. Reliability and Security Considerations

A load balancer is itself a critical component. If only one load-balancer instance exists, its failure can make all backends unreachable to clients.

A production architecture would consider:

- Multiple load-balancer instances and a reliable entry point.
- TLS termination and certificate management.
- Request-size limits and connection limits.
- Trusted handling of forwarded headers.
- Graceful shutdown and backend draining.
- Structured logs, metrics, tracing, and alerting.
- Controlled retry behavior.

Retries require special care: if a backend processes a request but its response is lost, retrying a non-idempotent operation can duplicate its effects.

## 9. How to Run the Example

From the repository root, start three backend instances in separate terminals:

```bash
python /load-balancing/backends/app_server.py --port 9001
```

```bash
python /load-balancing/backends/app_server.py --port 9002
```

```bash
python /load-balancing/backends/app_server.py --port 9003
```

Then start the load balancer:

```bash
python /load-balancing/load_balancer.py
```

Send repeated requests:

```bash
curl http://127.0.0.1:8080/
```

The responses identify which backend handled each request. Stop one backend and observe how health checks affect future routing.

## 10. Evolution Toward Production Readiness

The prototype is a starting point for exploring the internal mechanics of load balancing, not a replacement for mature production proxies.

Potential development milestones:

1. Add automated tests for scheduling, health transitions, and backend failures.
2. Introduce failure thresholds and recovery hysteresis.
3. Add bounded concurrency and streaming I/O.
4. Add metrics for request counts, latency, backend errors, and health state.
5. Implement configurable scheduling strategies.
6. Add graceful shutdown, backend draining, and TLS support.
7. Test behavior under sustained load and partial backend failure.
8. Evaluate multiple load-balancer instances and failure recovery.

Each addition should be justified by a specific reliability, performance, or operational requirement.

## 11. Key Takeaway

Load balancing is not merely distributing requests evenly. It is a routing and reliability problem involving scheduling, health state, resource limits, failure semantics, security, and observability.

Round Robin provides a useful baseline, but production-grade behavior requires careful handling of concurrency, backend failures, and the load balancer's own availability.

**Engineering principle:** Build the simplest implementation that exposes the essential mechanics, measure its limitations, and improve it through explicit design decisions rather than adding complexity without evidence.