# puretx

[![build](https://github.com/ohchanKyu/puretx/actions/workflows/build.yml/badge.svg)](https://github.com/ohchanKyu/puretx/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ohchankyu/puretx-spring-boot-starter.svg?label=maven%20central)](https://central.sonatype.com/artifact/io.github.ohchankyu/puretx-spring-boot-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Find the work that should not be inside your Spring transactions.**

HTTP calls, message publishing and slow work inside `@Transactional` hold a database connection —
and every lock it owns — hostage to something outside the database. puretx watches your running
application, reports it, and tells you where it came from. It does not fix it, block it, or roll
anything back.

---

## The problem

```java
@Transactional
public void createOrder(OrderRequest request) {
    orderRepository.save(request.toOrder());
    paymentClient.charge(request);          // HTTP call — connection and locks held while it waits
    kafkaTemplate.send("orders", event);    // published even if the transaction rolls back
}
```

Two failure modes, both of which only show up under load:

- **The connection pool runs dry.** Every in-flight order holds a connection for as long as the
  payment provider takes. A provider having a slow afternoon becomes your outage.
- **The data and the messages disagree.** The transaction rolls back; the message does not un-send.

Neither shows up in a code review, because neither looks wrong on the screen. They show up in
production, at the worst possible time. puretx moves that discovery forward to the pull request.

## Getting started

```kotlin
implementation("io.github.ohchankyu:puretx-spring-boot-starter:0.1.0-rc2")
```

```xml
<dependency>
    <groupId>io.github.ohchankyu</groupId>
    <artifactId>puretx-spring-boot-starter</artifactId>
    <version>0.1.0-rc2</version>
</dependency>
```

It is on Maven Central, so no extra repository is needed. Before 1.0 the API may still move
between minor versions; the changelog says when it does.

That is the whole setup. puretx defaults to `WARN`, instruments the transaction managers and HTTP
clients already in your context, and stays out of the way of everything else.

```yaml
puretx:
  mode: WARN            # OFF | WARN | FAIL
  max-duration: 3s
  app-packages: [com.acme]   # optional, but it makes the reports sharper
```

## What you get

One line per transaction, saying where its life went:

```
[puretx] OrderService.createOrder held a transaction open for 448ms — 431ms of it (96%) waiting on 1 external call
```

That share is the number nothing else can give you. A trace shows a slow request; a connection-pool
warning shows a symptom. This says how much of one transaction's life was spent outside the
database — and above it, exactly which call and which line of your code:

```
[puretx] IMPURE TRANSACTION detected
  tx       : OrderService.createOrder (started 15ms ago, JdbcTransactionManager)
  violation: HTTP POST https://pay.example.com/charge  (took 431ms)
  at       : com.acme.orders.PaymentGateway.charge(PaymentGateway.java:20)
  hint     : move the call outside the transaction, or defer it past the commit with
             @TransactionalEventListener(phase = AFTER_COMMIT)
  path     :
             at com.acme.orders.PaymentGateway.charge(PaymentGateway.java:20)
             at com.acme.orders.OrderService.createOrder(OrderService.java:29)
             at com.acme.orders.OrderController.create(OrderController.java:19)
```

The transaction line comes first on purpose. "You made an HTTP call" is easy to shrug off. "You made
an HTTP call 15ms into a transaction, and it took 431ms" is the sentence that gets it fixed.

The `path` is application frames only — the chain of your own code that led to the call, even when
the call itself happens several library frames deeper.

### Is it actually on?

puretx says so at startup, twice — what it is watching for, and what it managed to attach itself to:

```
[puretx] watching transactions — mode=WARN, max-duration=3s, detectors=[http, messaging, duration]
[puretx] instrumented 6 RestClient, 1 RestTemplate, 1 transaction manager
```

The second line is the one that matters. A detection library that reports nothing is ambiguous —
it could mean your code is clean, or it could mean nothing was ever wired up. If puretx cannot see
any transaction, or is watching for HTTP calls but reached no client, it says so at WARN rather
than leaving you to guess.

## Using it in CI

The pattern that works on an existing codebase:

- **Production and local: `WARN`.** Collect the violations you already have. Do not try to fix them
  all at once; put the ones you are not fixing this quarter into `puretx.ignore`.
- **Tests: `FAIL`.** New violations now break the build.

```yaml
# src/test/resources/application.yml
puretx:
  mode: FAIL
```

In `FAIL` mode the exception is thrown *before* the call goes out, so a test does not have to reach —
or wait for — the system it should not have been calling.

> **Put `@Transactional` on the service, not on the test.** A `@Transactional` test method wraps
> everything — fixtures, the code under test, the assertions — in one transaction that only exists
> because of the test harness. puretx ignores those by default (`puretx.detect-in-test-transactions`),
> because reporting them tells you about your test setup instead of your production code.

## What it detects

| | |
|---|---|
| **HTTP calls** | `RestTemplate`, `RestClient`, `WebClient` (when blocked on), Feign |
| **Message publishing** | Kafka, via any `ProducerFactory` in the context; RabbitMQ, via any `RabbitTemplate` |
| **Long transactions** | any transaction held open past `puretx.max-duration` |

Clients are instrumented however they were built: a bean made with `new RestTemplate()`, the
static `RestClient.builder()` or `WebClient.create()` is reached through a bean post-processor,
and a client built from an injected `RestTemplateBuilder`, `RestClient.Builder` or
`WebClient.Builder` inside a constructor is reached through Boot's customizers.

### What it cannot see

Detection is thread-bound and lives inside Spring's HTTP and messaging abstractions. Everything
below goes past it, and none of it is reported:

| | Why |
|---|---|
| A client built inside a method and thrown away | Never a bean, never a builder puretx customised |
| JDK `HttpClient`, OkHttp, Apache HttpClient used directly | Not a Spring abstraction; nothing to intercept |
| A vendor SDK with its own client — Slack, AWS, a payment provider's library | Same: it goes out over its own stack |
| Work handed to `@Async`, a `TaskExecutor` or `CompletableFuture.supplyAsync` | The call runs on a thread that has no transaction, even though the caller's does |
| A reactive transaction manager | The transaction is not bound to a thread, so there is no thread to ask |
| Messaging other than Kafka and RabbitMQ: JMS, SQS, Redis pub/sub | Not covered yet |

The first three have the same fix: wrap the call so puretx can see it.

```java
return Puretx.watch("Slack chat.postMessage",
        () -> slack.methods().chatPostMessage(request));
```

That behaves like any other detector: nothing happens outside a transaction, it logs in `WARN`,
and in `FAIL` it throws before the call. `Puretx.watch(MESSAGE_PUBLISH, …)` reports a publish
rather than an HTTP call.

## What else is out there

Worth being straight about this: puretx is not the only thing that looks at the problem. It is,
as far as I can find, the only one that checks at **runtime**, and that difference is the whole point.

| Tool | What it does | Where it stops |
|---|---|---|
| [Spring Transaction Inspector](https://plugins.jetbrains.com/plugin/28789-spring-transaction-inspector) (IntelliJ plugin) | Static inspection, including "external calls in transactions" | Sees the annotated method body. An HTTP call made two helpers deeper is invisible. IDE-only: no build step, and only for developers who installed it |
| ArchUnit | Class-level dependency rules; a [custom `ArchCondition`](https://github.com/TNG/ArchUnit/issues/1152) can match calls from a specific method | `getMethodCallsFromSelf()` is **direct calls only**. There is no transitive call-graph traversal, so `service → helper → RestTemplate` still slips through |
| `archunit-spring`, `archunit-cleancode-verifier` | Check **where** `@Transactional` is placed | Say nothing about what happens **inside** the transaction |
| HikariCP `leakDetectionThreshold` | Warns about connections held too long | Tells you the symptom, never the cause |
| APM (Datadog, Pinpoint, …) | Traces, after the fact | Already in production. Nothing to fail a build on |
| JetBrains Spring Debugger | Shows transaction boundaries and nesting while you debug | A debugger. Nothing runs in CI |

Every static tool above shares one ceiling: it can only see what a method's own body calls. Real
code does not look like that — the HTTP call lives in a client class, called by a helper, called by
the service that opened the transaction.

puretx checks at the moment of the call, where the answer is unambiguous and the indirection has
already happened:

```java
if (TransactionSynchronizationManager.isActualTransactionActive()) { … }
```

No bytecode weaving, no call-graph analysis, no guessing. It also means the transaction's name and
how long it has been open are simply available — the reported "1,204ms into OrderService.createOrder"
is not something static analysis could produce at any price.

## The false positives it will not raise

A warning that fires on correct code is worse than no warning: the first thing anyone does with a
noisy library is delete it, and the real violations go with it. These are all covered by tests, and
those tests were written before the detection was:

- `@TransactionalEventListener(phase = AFTER_COMMIT)` handlers — the fix puretx recommends, so
  flagging it would make the advice self-defeating.
- The same thing done by hand: work deferred with `registerSynchronization(...)` and run in
  `afterCommit`. (Spring still reports the transaction as active throughout that window, which is
  the single most common way to get this detection wrong.)
- Calls with no transaction open at all.
- `REQUIRES_NEW`: the inner transaction is tracked separately, and the outer one resumes afterwards.
- Publishing inside a Kafka-managed transaction — that is the transactional producer working as designed.
- Publishing on a transacted `RabbitTemplate` channel synchronised with the transaction — the
  channel commits after the database does, so a rollback takes the message back.
- Transactions opened by Spring's TestContext framework around a `@Transactional` test.

And when you have looked at a call and decided to keep it:

```java
Puretx.suppress(() -> auditClient.record(event));
```

## Configuration

| Property | Default | |
|---|---|---|
| `puretx.enabled` | `true` | Master switch. `false` wires nothing at all |
| `puretx.mode` | `WARN` | `OFF`, `WARN`, `FAIL` |
| `puretx.max-duration` | `3s` | Transactions held longer are reported. `0` disables |
| `puretx.ignore` | — | Class or package patterns to stay quiet about. `com.acme.legacy` covers everything below it; `com.acme.**.Generated` also works |
| `puretx.app-packages` | — | Your packages, so the reported call site is always your code |
| `puretx.include-call-path` | `true` | Log the chain of application frames |
| `puretx.call-path-depth` | `8` | Frames to keep |
| `puretx.record-limit` | `200` | Recent violations kept for `Puretx.violations()` |
| `puretx.log` | `true` | Whether the built-in listener logs |
| `puretx.detect-in-test-transactions` | `false` | Report inside Spring's test-managed transactions |
| `puretx.detectors.http` | `true` | |
| `puretx.detectors.messaging` | `true` | |
| `puretx.detectors.duration` | `true` | |

Violations are also available programmatically, which is useful for assertions:

```java
assertThat(Puretx.violations()).isEmpty();
```

`Puretx.violations()` reads one engine, and each Spring context that starts replaces it. If your
suite runs test classes in parallel across several contexts, inject the engine instead — it is
always the one belonging to the context under test:

```java
@Autowired PuretxEngine engine;

assertThat(engine.store().all()).isEmpty();
```

**Asserting on a WebClient call needs a wait.** Detection is synchronous — `FAIL` throws on the
calling thread like everywhere else — but the violation is *recorded* when the exchange
terminates, on whichever thread the client completes on. A blocking caller can return a moment
before the report lands, so an assertion made the instant `block()` returns is a race that happens
to pass on a fast loopback:

```java
await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(engine.store().all()).hasSize(1));
```

Nothing else needs this. `RestTemplate`, `RestClient`, Feign, Kafka and RabbitMQ all record on the
calling thread before the call returns.

**A RabbitMQ publish fails as an `UncategorizedAmqpException` in `FAIL` mode.** `RabbitTemplate`
wraps anything thrown before the publish, so the `ImpureTransactionException` is the cause. The
call still fails at the call site before the message leaves.

### Sending violations somewhere of your own

Register a `ViolationListener` bean and puretx calls it for every violation, and once more when
a transaction that produced any ends, with the summary. Listeners run on the thread that hit the
violation, so keep them cheap; anything they throw is swallowed.

With Micrometer on the classpath, four meters are published so `WARN` in production is more than a
log to grep:

| | |
|---|---|
| `puretx.violations` | how often, tagged `type` |
| `puretx.violation.duration` | how long the offending operation took |
| `puretx.transaction.external.wait` | per transaction, how long it waited in total |
| `puretx.transaction.external.share` | what share of its life that was |

The first two are tagged by violation type; the transaction meters carry no tags at all. The
call site and transaction name stay in the log — they are unbounded as tags, and a metrics
backend charges for cardinality. Switch it off with `puretx.metrics.enabled: false`.

## What it deliberately does not do

- **It does not roll back or retry.** Transaction demarcation belongs to Spring's transaction
  manager. A library that quietly interferes with it is a library that eventually causes an
  incident nobody can explain.
- **It does not block the call in production.** By the time puretx sees it, the damage is already
  a decision someone made. Throwing would turn a latency problem into a failed payment, and the
  library would be removed the same day.
- **It does not fix anything.** The right fix differs every time: move the call out, defer it to
  `afterCommit`, split the transaction, add an outbox — or leave it alone, because it is fast and
  idempotent and genuinely fine. That is a judgement call, and it is yours.

Also out of scope, on purpose: automatic after-commit publishing, outbox implementations, and N+1
query counting. All three are somebody else's library.

## Requirements

- Java 17+
- Spring Boot 3.2 or newer, including Boot 4 (puretx uses `TransactionExecutionListener`, added
  in Spring Framework 6.1). CI runs the whole test suite against the oldest and newest Boot 3
  and every Boot 4 line, so "supported" means "tested", not "probably fine".

Transaction tracking covers any `AbstractPlatformTransactionManager` — JDBC, JPA, JTA, Kafka.
Reactive transaction managers are not covered; puretx's detection is thread-bound.

## Modules

| | |
|---|---|
| `puretx-core` | The detection engine, with no framework dependency |
| `puretx-spring-boot-starter` | Auto-configuration and the Spring detectors — the one you depend on |
| `puretx-sample` | A runnable example. `./gradlew :puretx-sample:run`, then `curl -X POST localhost:8080/orders/impure` |

## Contributing

Bug reports, false positives above all, and pull requests are welcome. [CONTRIBUTING.md](CONTRIBUTING.md)
has the build, the conventions and what a good change looks like; [SECURITY.md](SECURITY.md) says
how to report a vulnerability privately.

## License

Apache License 2.0.
