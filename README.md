# puretx

[![build](https://github.com/ohchanKyu/puretx/actions/workflows/build.yml/badge.svg)](https://github.com/ohchanKyu/puretx/actions/workflows/build.yml)
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

Not on Maven Central yet — served from JitPack while the API settles.

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

implementation("com.github.ohchanKyu.puretx:puretx-spring-boot-starter:v0.1.0-rc1")
```

The coordinate changes to `io.github.ohchankyu:puretx-spring-boot-starter` on the first Central
release; the JitPack one is temporary.

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
| **Message publishing** | Kafka, via any `ProducerFactory` in the context |
| **Long transactions** | any transaction held open past `puretx.max-duration` |

Clients are instrumented as beans, however they were built — `new RestTemplate()`, the static
`RestClient.builder()`, `WebClient.create()`, or an injected builder all work. A client
constructed inside a method and never registered as a bean is the one case puretx cannot reach.

Only Spring's HTTP abstractions are covered. A vendor SDK that ships its own client — the Slack
SDK, the AWS SDK — goes out over its own stack and is invisible here. Wrap the call to make it
visible:

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

Nothing else needs this. `RestTemplate`, `RestClient`, Feign and Kafka all record on the calling
thread before the call returns.

and for sending them somewhere of your own — register a `ViolationListener` bean and puretx will
call it for every violation.

With Micrometer on the classpath, two meters are published so `WARN` in production is more than a
log to grep:

| | |
|---|---|
| `puretx.violations` | how often, tagged `type` |
| `puretx.violation.duration` | how long the offending operation took |
| `puretx.transaction.external.wait` | per transaction, how long it waited in total |
| `puretx.transaction.external.share` | what share of its life that was |

Tagged by violation type only. The call site and transaction name stay in the log — they are
unbounded as tags, and a metrics backend charges for cardinality. Switch it off with
`puretx.metrics.enabled: false`.

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
- Spring Boot 3.2+ (puretx uses `TransactionExecutionListener`, added in Spring Framework 6.1)

Transaction tracking covers any `AbstractPlatformTransactionManager` — JDBC, JPA, JTA, Kafka.
Reactive transaction managers are not covered; puretx's detection is thread-bound.

## Modules

| | |
|---|---|
| `puretx-core` | The detection engine, with no framework dependency |
| `puretx-spring-boot-starter` | Auto-configuration and the Spring detectors — the one you depend on |
| `puretx-sample` | A runnable example. `./gradlew :puretx-sample:run`, then `curl -X POST localhost:8080/orders/impure` |

## License

Apache License 2.0.
