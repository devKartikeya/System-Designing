import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Executable companion to clean-code.md. Requires Java 21+.
 *
 * Run: java CleanCode.java
 *
 * Everything lives in one file so it can be read top to bottom. In a real
 * codebase each
 * "module" below would be its own package, and the dependency rules in section
 * 5 would be
 * enforced mechanically (ArchUnit, JPMS, or build-module boundaries).
 *
 * Vocabulary used consistently in this file (section 1):
 * find* side-effect-free query on a port; may perform I/O
 * compute* and decide* pure derivation, no I/O, no clock, no randomness
 * assert* throws if an invariant or precondition does not hold
 * register/save/charge/enqueue commands: change state, return nothing
 * meaningful
 *
 * Section map:
 * 1 Naming value types, domain vocabulary, predicates
 * 2 Functions one abstraction level, parse-don't-validate, parameter objects
 * 3 Comments why-comments, contracts, owned TODOs
 * 4 Error handling error taxonomy, cause chaining, Result, single logging
 * point, retry
 * 5 Code organization ports and adapters, dependency direction
 * 6 Readability guard clauses, illegal states unrepresentable
 * 7 Code smells remedies are noted where they are applied
 * 8 Refactoring parallel change + characterization checks
 * 9 Side effects and state functional core / imperative shell, injected
 * nondeterminism
 * 10 Abstraction deep ports at volatile seams only
 */
public final class CleanCode {

    private CleanCode() {
    }

    // =====================================================================================
    // 1. NAMING
    // Names carry domain meaning; types carry units and invariants that names
    // cannot enforce.
    // Smell remedied: primitive obsession (raw long / String / UUID for domain
    // concepts).
    // =====================================================================================

    static final Currency USD = Currency.getInstance("USD");

    /**
     * Integer minor units: no floating-point drift, and the unit is part of the
     * type.
     */
    record Money(long minorUnits, Currency currency) {
        Money {
            Objects.requireNonNull(currency, "currency");
            if (minorUnits < 0) {
                throw new IllegalArgumentException("minorUnits must not be negative: " + minorUnits);
            }
        }

        static Money zero(Currency currency) {
            return new Money(0, currency);
        }

        static Money ofMinor(long minorUnits, Currency currency) {
            return new Money(minorUnits, currency);
        }

        Money plus(Money other) {
            assertSameCurrency(other);
            return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
        }

        Money times(int quantity) {
            if (quantity < 0)
                throw new IllegalArgumentException("quantity must not be negative");
            return new Money(Math.multiplyExact(minorUnits, (long) quantity), currency);
        }

        /**
         * Rounds down to the minor unit; the rounding mode is a pricing decision, kept
         * in one place.
         */
        Money percentOff(int percent) {
            if (percent < 0 || percent > 100)
                throw new IllegalArgumentException("percent out of range: " + percent);
            return new Money(Math.multiplyExact(minorUnits, 100L - percent) / 100, currency);
        }

        private void assertSameCurrency(Money other) {
            if (!currency.equals(other.currency)) {
                throw new CurrencyMismatchException(currency, other.currency);
            }
        }
    }

    /**
     * A programmer error: adding unlike currencies is a bug in the caller, never an
     * expected condition.
     */
    static final class CurrencyMismatchException extends IllegalArgumentException {
        CurrencyMismatchException(Currency left, Currency right) {
            super("currency mismatch: " + left + " vs " + right);
        }
    }

    record UserId(UUID value) {
        UserId {
            Objects.requireNonNull(value, "value");
        }

        static UserId random() {
            return new UserId(UUID.randomUUID());
        }
    }

    // =====================================================================================
    // 2. FUNCTIONS AND METHODS
    // Parse once at the boundary, then pass trusted, already-valid values inward.
    // Smell remedied: data clumps and flag arguments -> parameter objects (see
    // RetryPolicy, §4).
    // =====================================================================================

    record EmailAddress(String value) {
        private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

        EmailAddress {
            Objects.requireNonNull(value, "value");
            value = normalize(value);
            if (!SHAPE.matcher(value).matches()) {
                throw new IllegalArgumentException("not a well-formed email address");
            }
        }

        /**
         * For untrusted input: invalid data is an expected outcome, so it is not an
         * exception.
         */
        static Optional<EmailAddress> tryParse(String raw) {
            if (raw == null || !SHAPE.matcher(normalize(raw)).matches())
                return Optional.empty();
            return Optional.of(new EmailAddress(raw));
        }

        private static String normalize(String raw) {
            return raw.strip().toLowerCase(Locale.ROOT);
        }
    }

    enum RegistrationError {
        INVALID_EMAIL, DISPLAY_NAME_TOO_SHORT, DISPLAY_NAME_TOO_LONG
    }

    record RegistrationCommand(EmailAddress email, String displayName) {
        static final int DISPLAY_NAME_MIN_LENGTH = 2;
        static final int DISPLAY_NAME_MAX_LENGTH = 40;

        static Result<RegistrationCommand, RegistrationError> parse(String rawEmail, String rawDisplayName) {
            var email = EmailAddress.tryParse(rawEmail);
            if (email.isEmpty())
                return Result.err(RegistrationError.INVALID_EMAIL);

            var displayName = rawDisplayName == null ? "" : rawDisplayName.strip();
            if (displayName.length() < DISPLAY_NAME_MIN_LENGTH)
                return Result.err(RegistrationError.DISPLAY_NAME_TOO_SHORT);
            if (displayName.length() > DISPLAY_NAME_MAX_LENGTH)
                return Result.err(RegistrationError.DISPLAY_NAME_TOO_LONG);

            return Result.ok(new RegistrationCommand(email.get(), displayName));
        }
    }

    record User(UserId id, EmailAddress email, String displayName, Instant registeredAt) {
        static User register(UserId id, RegistrationCommand command, Instant now) {
            return new User(id, command.email(), command.displayName(), now);
        }
    }

    sealed interface DomainEvent permits UserRegistered {
    }

    record UserRegistered(UserId userId, Instant occurredAt) implements DomainEvent {
    }

    /**
     * Reads as policy: each line is one step at one level of abstraction; mechanism
     * is hidden
     * behind the collaborators.
     */
    static final class UserRegistrationService {
        private final Users users;
        private final Outbox outbox;
        private final Clock clock;
        private final Supplier<UserId> ids;

        UserRegistrationService(Users users, Outbox outbox, Clock clock, Supplier<UserId> ids) {
            this.users = users;
            this.outbox = outbox;
            this.clock = clock;
            this.ids = ids;
        }

        User register(RegistrationCommand command) {
            assertEmailAvailable(command.email());
            var user = User.register(ids.get(), command, clock.instant());
            users.save(user);
            // Publishing straight to a broker after save() is a dual write that can fail
            // between
            // the two steps. Production code writes the event in the same transaction as
            // the user
            // and relays it asynchronously; the Outbox port is where that guarantee lives.
            outbox.enqueue(new UserRegistered(user.id(), user.registeredAt()));
            return user;
        }

        private void assertEmailAvailable(EmailAddress email) {
            if (users.existsWithEmail(email))
                throw new EmailAlreadyRegisteredException();
        }
    }

    // =====================================================================================
    // 3. COMMENTS AND DOCUMENTATION
    // Comments carry what code cannot: the reason, the external constraint, the
    // invariant.
    // =====================================================================================

    record BillingEvent(String id, String subscriptionId, Instant created, String status) {
    }

    /**
     * Contract: not thread-safe; callers must serialize events per subscription
     * (for example by
     * partitioning the consumer on subscriptionId). apply() is idempotent for a
     * given event id.
     */
    static final class BillingEventProcessor {
        private final Set<String> processedEventIds = new HashSet<>();
        private final Map<String, Instant> lastAppliedAt = new HashMap<>();
        private final Map<String, String> statusBySubscription = new HashMap<>();

        /**
         * @return true if the event changed state; false if it was a duplicate or
         *         stale.
         */
        boolean apply(BillingEvent event) {
            // Webhooks are delivered at-least-once and may arrive out of order. Deduplicate
            // on
            // event id, then compare the creation time with what was already applied;
            // otherwise a
            // delayed "past_due" can overwrite a newer "canceled". See ADR-014.
            if (!processedEventIds.add(event.id()))
                return false;

            var latest = lastAppliedAt.get(event.subscriptionId());
            if (latest != null && !event.created().isAfter(latest))
                return false;

            lastAppliedAt.put(event.subscriptionId(), event.created());
            statusBySubscription.put(event.subscriptionId(), event.status());
            return true;
        }

        Optional<String> findStatus(String subscriptionId) {
            return Optional.ofNullable(statusBySubscription.get(subscriptionId));
        }
    }

    // TODO(PLAT-2291): replace the in-memory dedupe set with a bounded, persisted
    // store;
    // the set grows without limit and is lost on restart.

    // =====================================================================================
    // 4. ERROR HANDLING
    // Operational errors (expected): translate, retry, report.
    // Programmer errors (bugs): fail fast; never caught-and-continued below the
    // boundary.
    // =====================================================================================

    /** Base type for failures that a correct program can encounter. */
    abstract static class OperationalException extends RuntimeException {
        OperationalException(String message) {
            super(message);
        }

        OperationalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class EmailAlreadyRegisteredException extends OperationalException {
        EmailAlreadyRegisteredException() {
            super("email already registered");
        }
    }

    static final class PaymentUnavailableException extends OperationalException {
        PaymentUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Adapter-level detail. It must not leak past the billing module's boundary.
     */
    static final class GatewayTimeoutException extends RuntimeException {
        GatewayTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Expected failures in the signature: callers cannot ignore them by accident.
     */
    sealed interface Result<T, E> {
        record Ok<T, E>(T value) implements Result<T, E> {
        }

        record Err<T, E>(E error) implements Result<T, E> {
        }

        static <T, E> Result<T, E> ok(T value) {
            return new Ok<>(value);
        }

        static <T, E> Result<T, E> err(E error) {
            return new Err<>(error);
        }

        default T orElseThrow(Function<? super E, ? extends RuntimeException> toException) {
            return switch (this) {
                case Ok<T, E> ok -> ok.value();
                case Err<T, E> err -> throw toException.apply(err.error());
            };
        }
    }

    interface PaymentGateway {
        void charge(String accountRef, Money amount, String idempotencyKey);
    }

    /**
     * Parameter object: the call site names every argument; order is no longer
     * load-bearing.
     */
    record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        RetryPolicy {
            if (maxAttempts < 1)
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            Objects.requireNonNull(baseDelay, "baseDelay");
            Objects.requireNonNull(maxDelay, "maxDelay");
            if (baseDelay.isNegative() || maxDelay.compareTo(baseDelay) < 0) {
                throw new IllegalArgumentException("require 0 <= baseDelay <= maxDelay");
            }
        }
    }

    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;

        static Sleeper real() {
            return duration -> Thread.sleep(duration.toMillis());
        }
    }

    static final class Retry {
        private Retry() {
        }

        /**
         * Only pass operations that are safe to repeat (naturally idempotent, or made
         * so with an
         * idempotency key). Backoff is exponential with full jitter so that clients
         * that failed
         * together do not retry together.
         */
        static <T> T withBackoff(
                Supplier<T> idempotentOperation,
                RetryPolicy policy,
                Predicate<RuntimeException> isRetryable,
                Sleeper sleeper,
                RandomGenerator random) {
            for (int attempt = 1;; attempt++) {
                try {
                    return idempotentOperation.get();
                } catch (RuntimeException failure) {
                    boolean outOfAttempts = attempt >= policy.maxAttempts();
                    if (outOfAttempts || !isRetryable.test(failure))
                        throw failure;
                    pause(sleeper, computeBackoff(policy, attempt, random));
                }
            }
        }

        static void run(
                Runnable idempotentOperation,
                RetryPolicy policy,
                Predicate<RuntimeException> isRetryable,
                Sleeper sleeper,
                RandomGenerator random) {
            withBackoff(() -> {
                idempotentOperation.run();
                return null;
            }, policy, isRetryable, sleeper, random);
        }

        static Duration computeBackoff(RetryPolicy policy, int attempt, RandomGenerator random) {
            long exponentialMs = policy.baseDelay().toMillis() << Math.min(attempt - 1, 20);
            long capMs = Math.min(policy.maxDelay().toMillis(), exponentialMs);
            return Duration.ofMillis(random.nextLong(capMs + 1));
        }

        private static void pause(Sleeper sleeper, Duration duration) {
            try {
                sleeper.sleep(duration);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while backing off", interrupted);
            }
        }
    }

    /**
     * The layer that can decide: it retries, and translates what cannot be
     * recovered.
     */
    static final class BillingService {
        private static final RetryPolicy POLICY = new RetryPolicy(3, Duration.ofMillis(200), Duration.ofSeconds(2));

        private final PaymentGateway gateway;
        private final Sleeper sleeper;
        private final RandomGenerator random;

        BillingService(PaymentGateway gateway, Sleeper sleeper, RandomGenerator random) {
            this.gateway = gateway;
            this.sleeper = sleeper;
            this.random = random;
        }

        void charge(String accountRef, Money amount, String idempotencyKey) {
            try {
                Retry.run(
                        () -> gateway.charge(accountRef, amount, idempotencyKey),
                        POLICY,
                        failure -> failure instanceof GatewayTimeoutException,
                        sleeper,
                        random);
            } catch (GatewayTimeoutException cause) {
                // Translate at the boundary so persistence/gateway details do not leak upward,
                // but keep the original as the cause. Anything unrecognized propagates
                // untouched.
                throw new PaymentUnavailableException(
                        "gateway timed out charging account " + accountRef + " (key " + idempotencyKey + ")",
                        cause);
            }
        }
    }

    record Response(int status, String message, String correlationId) {
    }

    interface ErrorLog {
        void error(String correlationId, String message, Throwable cause);
    }

    /**
     * The one place failures are logged and mapped to responses. Callers receive a
     * stable message
     * and a correlation id; stack traces and internals stay in the log.
     */
    static final class HttpBoundary {
        private final ErrorLog log;
        private final Supplier<String> correlationIds;

        HttpBoundary(ErrorLog log, Supplier<String> correlationIds) {
            this.log = log;
            this.correlationIds = correlationIds;
        }

        Response handle(Supplier<String> action) {
            var correlationId = correlationIds.get();
            try {
                return new Response(200, action.get(), correlationId);
            } catch (EmailAlreadyRegisteredException e) {
                return fail(409, "Email already registered", correlationId, e);
            } catch (PaymentUnavailableException e) {
                return fail(503, "Payment service temporarily unavailable", correlationId, e);
            } catch (RuntimeException e) {
                // Programmer error or unknown failure. Below this point it failed fast; here it
                // is
                // converted into a generic 500. In a worker or CLI, let the process die
                // instead.
                return fail(500, "Internal error", correlationId, e);
            }
        }

        private Response fail(int status, String publicMessage, String correlationId, RuntimeException cause) {
            log.error(correlationId, publicMessage, cause);
            return new Response(status, publicMessage, correlationId);
        }
    }

    // =====================================================================================
    // 5. CODE ORGANIZATION
    // Dependencies point toward the domain. Ports are declared next to the domain
    // that needs
    // them; adapters implement them and depend inward. Nothing in sections 1-4
    // imports an adapter.
    // =====================================================================================

    interface Users {
        boolean existsWithEmail(EmailAddress email);

        void save(User user);
    }

    interface Outbox {
        void enqueue(DomainEvent event);
    }

    // --- Adapters (infrastructure). Swap for SQL / Kafka / etc. without touching
    // the domain. ---

    static final class InMemoryUsers implements Users {
        private final Map<EmailAddress, User> byEmail = new HashMap<>();

        @Override
        public boolean existsWithEmail(EmailAddress email) {
            return byEmail.containsKey(email);
        }

        @Override
        public void save(User user) {
            byEmail.put(user.email(), user);
        }
    }

    static final class InMemoryOutbox implements Outbox {
        final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void enqueue(DomainEvent event) {
            events.add(event);
        }
    }

    // =====================================================================================
    // 6. READABILITY AND CONSISTENCY
    // =====================================================================================

    record LineItem(String sku, int quantity, Money unitPrice) {
        LineItem {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (quantity < 1)
                throw new IllegalArgumentException("quantity must be >= 1");
        }

        Money subtotal() {
            return unitPrice.times(quantity);
        }
    }

    record Order(String id, Currency currency, List<LineItem> items, boolean digital) {
        Order {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(currency, "currency");
            items = List.copyOf(items); // defensive copy: later changes to the caller's list cannot leak in
        }

        /**
         * Derived, not stored: a cached total is a second copy of knowledge that can
         * drift.
         */
        Money computeTotal() {
            return items.stream().map(LineItem::subtotal).reduce(Money.zero(currency), Money::plus);
        }

        Order withItem(LineItem item) {
            var next = new ArrayList<>(items);
            next.add(item);
            return new Order(id, currency, next, digital);
        }
    }

    static final class ShippingCalculator {
        static final long FLAT_RATE_MINOR_UNITS = 499;
        static final long FREE_SHIPPING_THRESHOLD_MINOR_UNITS = 5_000;

        private ShippingCalculator() {
        }

        /**
         * Guard clauses: each line is a self-contained fact, and the happy path sits at
         * the margin.
         */
        static Money computeShippingCost(Order order) {
            var free = Money.zero(order.currency());
            if (order.items().isEmpty())
                return free;
            if (order.digital())
                return free;
            if (order.computeTotal().minorUnits() >= FREE_SHIPPING_THRESHOLD_MINOR_UNITS)
                return free;
            return Money.ofMinor(FLAT_RATE_MINOR_UNITS, order.currency());
        }
    }

    /**
     * Each state carries exactly the data valid for it. "Success with an error" or
     * "loading with
     * data" cannot be constructed, so no defensive code or tests are needed for
     * them.
     */
    sealed interface RequestState<T> {
        record Idle<T>() implements RequestState<T> {
        }

        record Loading<T>() implements RequestState<T> {
        }

        record Success<T>(T data) implements RequestState<T> {
            public Success {
                Objects.requireNonNull(data, "data");
            }
        }

        record Failure<T>(String reason) implements RequestState<T> {
        }
    }

    /**
     * No default branch: adding a variant becomes a compile error here instead of a
     * latent bug.
     */
    static <T> String describe(RequestState<T> state) {
        return switch (state) {
            case RequestState.Idle<T> idle -> "idle";
            case RequestState.Loading<T> loading -> "loading";
            case RequestState.Success<T>(var data) -> "success: " + data;
            case RequestState.Failure<T>(var reason) -> "failed: " + reason;
        };
    }

    // =====================================================================================
    // 7 + 10. CODE SMELLS AND ABSTRACTION
    // Feature envy remedied: "is this account delinquent?" lives on Account, not in
    // callers.
    // Abstraction: the port names a domain question and hides how it is answered. A
    // generic
    // `findOne(Map<String,Object> query)` wrapper would add a layer and no meaning.
    // =====================================================================================

    record Account(String id, Money amountDue, Instant dueDate) {
        boolean isDelinquent(Instant cutoff) {
            return amountDue.minorUnits() > 0 && dueDate.isBefore(cutoff);
        }
    }

    interface Accounts {
        List<Account> findDelinquentSince(Instant cutoff);
    }

    static final class InMemoryAccounts implements Accounts {
        private final List<Account> all;

        InMemoryAccounts(List<Account> all) {
            this.all = List.copyOf(all);
        }

        @Override
        public List<Account> findDelinquentSince(Instant cutoff) {
            return all.stream().filter(account -> account.isDelinquent(cutoff)).toList();
        }
    }

    // =====================================================================================
    // 8. REFACTORING
    // Parallel change: expand (add the new API), migrate callers, contract (delete
    // the old one).
    // The legacy method survives as a thin shim over the new one so behavior cannot
    // diverge,
    // and a characterization check pins what the old code actually did (see
    // demoRefactoring).
    // =====================================================================================

    static final class Pricing {
        private Pricing() {
        }

        static Money withPercentOff(Money total, int percentOff) {
            return total.percentOff(percentOff);
        }

        /**
         * @deprecated expand/migrate/contract in progress: use {@link #withPercentOff}.
         *             Legacy
         *             callers are USD-only. Remove once the last caller is migrated
         *             (PLAT-2310).
         */
        @Deprecated(forRemoval = true)
        static long discountedCents(long cents, int percentOff) {
            return withPercentOff(Money.ofMinor(cents, USD), percentOff).minorUnits();
        }
    }

    // =====================================================================================
    // 9. SIDE EFFECTS AND STATE
    // Functional core: decideRenewal is a pure function of its arguments.
    // Imperative shell: RenewalJob gathers inputs, calls the core, applies effects.
    // Time, ids, randomness and sleeping are injected, so behavior is reproducible
    // in tests.
    // Command-query separation: findDue is a query; billing.charge is a command.
    // =====================================================================================

    enum SubscriptionStatus {
        ACTIVE, CANCELLED
    }

    record Subscription(String id, String accountRef, Instant renewsAt, Money price, SubscriptionStatus status) {
        boolean isCancelled() {
            return status == SubscriptionStatus.CANCELLED;
        }
    }

    enum SkipReason {
        CANCELLED, NOT_DUE
    }

    sealed interface RenewalDecision {
        record Skip(SkipReason reason) implements RenewalDecision {
        }

        record Charge(Money amount) implements RenewalDecision {
        }
    }

    /** Pure: no I/O, no clock, no globals. Trivially testable with plain values. */
    static RenewalDecision decideRenewal(Subscription subscription, Instant now) {
        if (subscription.isCancelled())
            return new RenewalDecision.Skip(SkipReason.CANCELLED);
        if (now.isBefore(subscription.renewsAt()))
            return new RenewalDecision.Skip(SkipReason.NOT_DUE);
        return new RenewalDecision.Charge(subscription.price());
    }

    interface Subscriptions {
        List<Subscription> findDue(Instant now);
    }

    static final class RenewalJob {
        private final Subscriptions subscriptions;
        private final BillingService billing;
        private final Clock clock;

        RenewalJob(Subscriptions subscriptions, BillingService billing, Clock clock) {
            this.subscriptions = subscriptions;
            this.billing = billing;
            this.clock = clock;
        }

        /**
         * @return number of charges issued. Safe to re-run: the key makes each renewal
         *         idempotent.
         */
        int run() {
            var now = clock.instant();
            int charged = 0;
            for (var subscription : subscriptions.findDue(now)) {
                if (decideRenewal(subscription, now) instanceof RenewalDecision.Charge charge) {
                    billing.charge(subscription.accountRef(), charge.amount(), idempotencyKeyFor(subscription));
                    charged++;
                }
            }
            return charged;
        }

        static String idempotencyKeyFor(Subscription subscription) {
            return "renewal:" + subscription.id() + ":" + subscription.renewsAt();
        }
    }

    // =====================================================================================
    // DEMO + SELF-CHECKS (test doubles live here, away from the production code
    // above)
    // =====================================================================================

    /**
     * Fails with a timeout for the first N attempts, then succeeds. Deduplicates on
     * idempotency key.
     */
    static final class FlakyGateway implements PaymentGateway {
        private final int failuresBeforeSuccess;
        int attempts = 0;
        final Map<String, Money> chargedByKey = new HashMap<>();

        FlakyGateway(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public void charge(String accountRef, Money amount, String idempotencyKey) {
            attempts++;
            if (attempts <= failuresBeforeSuccess)
                throw new GatewayTimeoutException("simulated timeout");
            chargedByKey.putIfAbsent(idempotencyKey, amount);
        }
    }

    private static int failedChecks = 0;

    private static void check(String description, boolean condition) {
        System.out.printf("  [%s] %s%n", condition ? "PASS" : "FAIL", description);
        if (!condition)
            failedChecks++;
    }

    private static void section(String title) {
        System.out.printf("%n== %s%n", title);
    }

    private static void demoRegistration() {
        section("1-2, 5: naming, parse-don't-validate, ports and adapters");
        var clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC);
        var counter = new AtomicLong();
        var users = new InMemoryUsers();
        var outbox = new InMemoryOutbox();
        var service = new UserRegistrationService(
                users, outbox, clock, () -> new UserId(new UUID(0, counter.incrementAndGet())));

        check("malformed email is a value-level failure, not an exception",
                RegistrationCommand.parse("not-an-email",
                        "Ada") instanceof Result.Err<RegistrationCommand, RegistrationError> err
                        && err.error() == RegistrationError.INVALID_EMAIL);

        var command = RegistrationCommand.parse("  Ada@Example.COM ", " Ada ")
                .orElseThrow(error -> new IllegalStateException("unexpected: " + error));
        check("input is normalized once, at the boundary", command.email().value().equals("ada@example.com"));

        var user = service.register(command);
        check("registration saves the user and enqueues exactly one event",
                users.existsWithEmail(command.email()) && outbox.events.size() == 1);
        check("registration time comes from the injected clock", user.registeredAt().equals(clock.instant()));

        boolean rejected = false;
        try {
            service.register(command);
        } catch (EmailAlreadyRegisteredException expected) {
            rejected = true;
        }
        check("duplicate registration is rejected as an operational error", rejected);
    }

    private static void demoErrorHandling() {
        section("4: error handling, translation, single logging point, retry");
        var noSleep = new ArrayList<Duration>();
        Sleeper recordingSleeper = noSleep::add;
        var random = new java.util.Random(42);
        var charge = Money.ofMinor(1_999, USD);

        var recovering = new FlakyGateway(2);
        new BillingService(recovering, recordingSleeper, random).charge("acct-1", charge, "key-1");
        check("transient timeouts are retried until success", recovering.attempts == 3);
        check("one backoff pause per retry, each within its exponential cap",
                noSleep.size() == 2
                        && noSleep.get(0).toMillis() <= 200
                        && noSleep.get(1).toMillis() <= 400);

        var failing = new FlakyGateway(10);
        PaymentUnavailableException translated = null;
        try {
            new BillingService(failing, recordingSleeper, random).charge("acct-2", charge, "key-2");
        } catch (PaymentUnavailableException e) {
            translated = e;
        }
        check("retries are bounded by the policy", failing.attempts == 3);
        check("failure is translated and the original is preserved as the cause",
                translated != null && translated.getCause() instanceof GatewayTimeoutException);

        var logged = new ArrayList<String>();
        var correlation = new AtomicLong();
        var boundary = new HttpBoundary(
                (id, message, cause) -> logged.add(id + ":" + cause.getClass().getSimpleName()),
                () -> "req-" + correlation.incrementAndGet());

        var ok = boundary.handle(() -> "done");
        var conflict = boundary.handle(() -> {
            throw new EmailAlreadyRegisteredException();
        });
        var bug = boundary.handle(() -> {
            throw new IllegalStateException("secret internal detail: jdbc:postgresql://db-internal/prod");
        });

        check("success maps to 200", ok.status() == 200);
        check("operational errors map to specific statuses", conflict.status() == 409);
        check("unknown failures map to a generic 500 that leaks no internals",
                bug.status() == 500 && !bug.message().contains("jdbc"));
        check("each failure is logged exactly once, with its correlation id",
                logged.equals(List.of("req-2:EmailAlreadyRegisteredException", "req-3:IllegalStateException")));
    }

    private static void demoReadability() {
        section("6: guard clauses, illegal states unrepresentable");
        var shirt = new LineItem("shirt", 2, Money.ofMinor(1_000, USD));
        var cheap = new Order("o-1", USD, List.of(shirt), false);
        var large = cheap.withItem(new LineItem("jacket", 1, Money.ofMinor(9_000, USD)));
        var digital = new Order("o-2", USD, List.of(shirt), true);
        var empty = new Order("o-3", USD, List.of(), false);

        check("under the threshold pays the flat rate",
                ShippingCalculator.computeShippingCost(cheap).minorUnits() == 499);
        check("at or above the threshold ships free",
                large.computeTotal().minorUnits() == 11_000
                        && ShippingCalculator.computeShippingCost(large).minorUnits() == 0);
        check("digital and empty orders ship free",
                ShippingCalculator.computeShippingCost(digital).minorUnits() == 0
                        && ShippingCalculator.computeShippingCost(empty).minorUnits() == 0);

        check("every state renders from exactly the data it owns",
                describe(new RequestState.Idle<String>()).equals("idle")
                        && describe(new RequestState.Loading<String>()).equals("loading")
                        && describe(new RequestState.Success<>("42 items")).equals("success: 42 items")
                        && describe(new RequestState.Failure<String>("timeout")).equals("failed: timeout"));

        boolean mixedCurrencyRejected = false;
        try {
            Money.ofMinor(1, USD).plus(Money.ofMinor(1, Currency.getInstance("EUR")));
        } catch (CurrencyMismatchException expected) {
            mixedCurrencyRejected = true;
        }
        check("invariant owned by the type: unlike currencies cannot be added", mixedCurrencyRejected);
    }

    private static void demoDocumentedContract() {
        section("3: why-comments backed by behavior");
        var processor = new BillingEventProcessor();
        var t = Instant.parse("2026-10-17T10:00:00Z");

        check("first event is applied",
                processor.apply(new BillingEvent("evt-a", "sub-1", t.plusSeconds(10), "active")));
        check("redelivery of the same event id is ignored",
                !processor.apply(new BillingEvent("evt-a", "sub-1", t.plusSeconds(10), "active")));
        check("a delayed older event cannot overwrite newer state",
                !processor.apply(new BillingEvent("evt-b", "sub-1", t.plusSeconds(5), "past_due")));
        check("a newer event is applied",
                processor.apply(new BillingEvent("evt-c", "sub-1", t.plusSeconds(20), "canceled")));
        check("final state reflects the newest event", processor.findStatus("sub-1").orElse("").equals("canceled"));
    }

    private static void demoFunctionalCore() {
        section("9: functional core / imperative shell");
        var now = Instant.parse("2026-10-17T10:00:00Z");
        var price = Money.ofMinor(1_500, USD);
        var due = new Subscription("s1", "acct-1", now.minusSeconds(60), price, SubscriptionStatus.ACTIVE);
        var notDue = new Subscription("s2", "acct-2", now.plusSeconds(60), price, SubscriptionStatus.ACTIVE);
        var cancelled = new Subscription("s3", "acct-3", now.minusSeconds(60), price, SubscriptionStatus.CANCELLED);

        check("pure core: due subscription is charged", decideRenewal(due, now) instanceof RenewalDecision.Charge);
        check("pure core: future renewal is skipped as NOT_DUE",
                decideRenewal(notDue, now) instanceof RenewalDecision.Skip(var reason) && reason == SkipReason.NOT_DUE);
        check("pure core: cancelled subscription is skipped as CANCELLED",
                decideRenewal(cancelled, now) instanceof RenewalDecision.Skip(var reason)
                        && reason == SkipReason.CANCELLED);

        var gateway = new FlakyGateway(0);
        var billing = new BillingService(gateway, Sleeper.real(), new java.util.Random(1));
        Subscriptions repository = at -> List.of(due, cancelled);
        var job = new RenewalJob(repository, billing, Clock.fixed(now, ZoneOffset.UTC));

        check("shell issues one charge for the one chargeable subscription", job.run() == 1);
        job.run();
        check("re-running the job does not double-charge (idempotency key)", gateway.chargedByKey.size() == 1);
    }

    private static void demoAbstractionAndRefactoring() {
        section("7, 8, 10: smells, parallel change, characterization");
        var now = Instant.parse("2026-10-17T10:00:00Z");
        Accounts accounts = new InMemoryAccounts(List.of(
                new Account("a1", Money.ofMinor(500, USD), now.minus(Duration.ofDays(40))),
                new Account("a2", Money.zero(USD), now.minus(Duration.ofDays(40))),
                new Account("a3", Money.ofMinor(700, USD), now.plus(Duration.ofDays(5)))));

        var delinquentAccounts = accounts.findDelinquentSince(now);
        check("the port answers a domain question; callers know nothing about storage",
                delinquentAccounts.size() == 1 && delinquentAccounts.get(0).id().equals("a1"));

        // Characterization: pin what the legacy method actually does (including
        // rounding down)
        // before and while it is replaced, so migration cannot silently change
        // behavior.
        check("characterization: legacy 10% off 999 yields 899 (rounds down)", Pricing.discountedCents(999, 10) == 899);
        check("legacy shim and new API agree",
                Pricing.discountedCents(1_000, 25) == Pricing.withPercentOff(Money.ofMinor(1_000, USD), 25)
                        .minorUnits());

        var callerOwnedList = new ArrayList<>(List.of(new LineItem("pen", 1, Money.ofMinor(150, USD))));
        var order = new Order("o-9", USD, callerOwnedList, false);
        callerOwnedList.clear();
        check("defensive copy: mutating the caller's list cannot corrupt the order", order.items().size() == 1);
    }

    public static void main(String[] args) {
        System.out.println("Clean Code, executable edition");
        demoRegistration();
        demoDocumentedContract();
        demoErrorHandling();
        demoReadability();
        demoFunctionalCore();
        demoAbstractionAndRefactoring();

        System.out.printf("%n%s%n", failedChecks == 0 ? "All checks passed." : failedChecks + " check(s) FAILED.");
        if (failedChecks > 0)
            System.exit(1);
    }
}