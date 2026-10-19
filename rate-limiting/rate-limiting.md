# Rate Limiting

## 1. Overview

Rate limiting is a traffic-control mechanism that restricts how many requests a client can make within a defined period.

Its purpose is not simply to reject excessive requests. It helps protect system resources, maintain predictable service behavior, reduce abuse, and allocate capacity fairly across clients.

For example, an API might permit a client to make 100 requests per minute. Requests beyond the configured limit may be rejected until capacity becomes available again.

Rate limiting is commonly applied to APIs, authentication endpoints, public-facing services, and resource-intensive operations.

## 2. Why Rate Limiting Matters

An application without appropriate traffic controls may be exposed to:

- Excessive requests from a single client.
- Accidental request storms caused by buggy clients.
- Brute-force attempts against authentication endpoints.
- Resource exhaustion and increased latency.
- Unequal resource consumption among clients.
- Unexpected infrastructure costs.

Rate limiting is one layer of defense. It does not, by itself, prevent distributed denial-of-service attacks or guarantee fair resource allocation.

## 3. Where Rate Limiting Fits

```text
                 Client
                   |
                   v
            API Gateway / Proxy
                   |
             Rate Limiter
                   |
          +--------+--------+
          |                 |
      Within limit      Over limit
          |                 |
          v                 v
      Application       HTTP 429
          |
          v
       Response
```

A rate limiter can run inside an application, in an API gateway, at a reverse proxy, or in a dedicated service.

The placement determines which traffic it can observe, how early it can reject requests, and whether its state is shared across application instances.

## 4. Core Design Questions

Before choosing an algorithm, define the policy.

### What is being limited?

Possible dimensions include:

- Client IP address.
- Authenticated user ID.
- API key or tenant ID.
- Endpoint or operation.
- Global service traffic.
- A combination of these dimensions.

IP-based limits require care because multiple legitimate users may share an IP address, while distributed clients can use many IP addresses.

### What is the limit?

A policy should define:

- Maximum permitted requests.
- Time interval or refill rate.
- Burst allowance.
- Identity key.
- Behavior when the limit is exceeded.
- Whether limits differ by endpoint or customer tier.

A request-count limit may be insufficient for endpoints with significantly different computational costs. Some systems use weighted request costs or concurrent-operation limits as well.

## 5. Common Rate-Limiting Algorithms

### 5.1 Fixed Window Counter

Counts requests within fixed time intervals.

For example, a policy permits 100 requests per minute. The counter resets at the start of each minute.

**Advantages**
- Simple implementation.
- Low memory usage.
- Efficient counter updates.

**Disadvantages**
- Boundary bursts can exceed the intended short-term traffic rate.

A client might send 100 requests immediately before the minute ends and another 100 immediately after it begins.

### 5.2 Sliding Window Log

Stores timestamps for recent requests and counts those within the active time window.

**Advantages**
- Accurate rolling-window enforcement.
- Avoids fixed-window boundary bursts.

**Disadvantages**
- Potentially high memory usage.
- Request processing and storage costs increase with request volume.

### 5.3 Sliding Window Counter

Combines the current window's counter with a weighted contribution from the previous window.

**Advantages**
- Lower memory requirements than storing every request timestamp.
- Smoother enforcement than a basic fixed window.

**Disadvantages**
- Approximate rather than exact rolling-window accounting.

### 5.4 Token Bucket

Tokens are added to a bucket at a configured refill rate. Each request consumes tokens. A request is permitted when sufficient tokens are available.

For a bucket capacity \(B\), refill rate \(r\), and elapsed time \(\Delta t\):

\[
T_{\text{new}} = \min(B,\ T_{\text{old}} + r\Delta t)
\]

A request with cost \(c\) is allowed when:

\[
T_{\text{new}} \ge c
\]

After acceptance, the bucket is reduced by \(c\).

**Advantages**
- Allows controlled bursts.
- Enforces a sustainable average request rate.
- Well suited to APIs with bursty traffic.

**Disadvantages**
- Requires state per rate-limit key.
- Distributed enforcement requires coordination or a defined approximation strategy.

### 5.5 Leaky Bucket

Models requests as entering a bucket that drains at a controlled rate. Depending on the implementation, excess requests are queued or rejected.

**Advantages**
- Can smooth traffic into a more consistent output rate.
- Useful when downstream processing needs controlled throughput.

**Disadvantages**
- Queuing adds latency and requires bounded queue capacity.
- Queueing every request is not always appropriate for interactive APIs.

## 6. Choosing an Algorithm

| Requirement | Potential choice |
|---|---|
| Simple request quota | Fixed window |
| Accurate rolling-window quota | Sliding window log |
| Memory-efficient rolling approximation | Sliding window counter |
| Controlled bursts and sustained rate | Token bucket |
| Smooth downstream processing | Leaky bucket or bounded queue |

No algorithm is universally best. Choose based on the policy, acceptable burst behavior, storage constraints, and consistency requirements.

## 7. HTTP Behavior

When a client exceeds its permitted rate, APIs commonly return:

- **HTTP 429 Too Many Requests:** The request was rejected because a rate limit was exceeded.
- **`Retry-After`:** An optional header indicating when the client may retry.
- **Rate-limit metadata:** Headers that communicate applicable limits or remaining capacity, following the API's chosen convention.

Clients should implement backoff and jitter rather than retrying continuously. Otherwise, retries can amplify overload.

## 8. Distributed Rate Limiting

A process-local counter is straightforward, but it creates a correctness problem when an application runs on multiple instances.

Consider three application servers, each allowing 100 requests per minute per client. If each maintains its own independent counter, a client may be able to make approximately 300 requests across the fleet, depending on request distribution.

Common approaches include:

### Shared State

Store rate-limit state in a shared system such as Redis. Atomic operations or scripts are needed to prevent concurrent requests from incorrectly exceeding the limit.

**Trade-off:** Better coordination, but additional network latency and a dependency on the shared state store.

### Gateway Enforcement

Enforce policies at a shared gateway or proxy.

**Trade-off:** Centralizes enforcement but requires the gateway to scale and remain available.

### Local Approximation

Maintain local limits on each instance.

**Trade-off:** Lower coordination overhead, but the effective global limit may differ from the configured per-instance limit.

### Partitioned or Hybrid Designs

Partition identities or combine local and global limits.

**Trade-off:** Can reduce contention, but introduces more complex allocation and consistency rules.

The right choice depends on whether strict global enforcement is required or approximate enforcement is acceptable.

## 9. Failure Handling

A production design must define what happens when the rate-limit state store is unavailable.

**Fail-open:** Allow requests when enforcement cannot be performed.

- Preserves availability.
- May expose expensive endpoints to excessive traffic.

**Fail-closed:** Reject requests when enforcement cannot be performed.

- Preserves strict enforcement.
- Can deny legitimate traffic during an infrastructure failure.

A hybrid policy can treat endpoints differently. For example, a security-sensitive authentication endpoint may require stricter behavior than a low-risk read-only endpoint.

The policy should be explicit, observable, and tested.

## 10. Security and Operational Considerations

A robust implementation should account for:

- Trusted client identity and proxy-header handling.
- Atomic updates under concurrent traffic.
- Memory limits and expiration of inactive keys.
- High-cardinality identities and storage exhaustion.
- Endpoint-specific policies and weighted request costs.
- Clear client-facing rejection responses.
- Metrics for allowed, rejected, and failed checks.
- Monitoring of rate-limit-store latency and errors.
- Protection against clients deliberately rotating identities.

Rate limiting should complement authentication, authorization, quotas, abuse detection, and infrastructure-level traffic protection.

## 11. Testing Strategy

A serious implementation should test more than whether a counter reaches a threshold.

Important cases include:

1. Requests below the limit are accepted.
2. Requests beyond the limit are rejected.
3. Capacity becomes available again as expected.
4. Concurrent requests cannot bypass atomic enforcement.
5. Different identities maintain independent limits.
6. Expired keys are cleaned up safely.
7. Clock behavior does not create unexpected policy violations.
8. Multiple application instances enforce the intended shared policy.
9. Store outages follow the documented fail-open or fail-closed behavior.
10. Legitimate traffic is not unfairly blocked by incorrect client identity.

Load tests should measure both enforcement correctness and the additional latency introduced by the limiter.

## 12. Evolution Toward Production Readiness

A practical implementation roadmap:

1. Implement a token bucket with deterministic tests.
2. Add per-client state and safe expiration.
3. Define policy configuration independently of algorithm code.
4. Add concurrency-safe state updates.
5. Introduce shared Redis-backed enforcement if global coordination is required.
6. Define behavior during store outages and network timeouts.
7. Add metrics, structured logs, and load tests.
8. Evaluate per-route quotas, tenant fairness, and weighted request costs.
9. Test distributed behavior across multiple application instances.

Each stage should address a concrete requirement rather than adding infrastructure by default.

## 13. Key Takeaway

Rate limiting is a policy-enforcement and resource-allocation problem, not merely a counter in middleware.

Algorithm choice determines burst behavior; state placement determines distributed correctness; and failure policy determines how the application behaves when enforcement infrastructure is unavailable.

**Engineering principle:** Define the traffic policy first, select an algorithm that satisfies it, and make the consistency, performance, and failure trade-offs explicit.