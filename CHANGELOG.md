# Changelog

All notable changes to puretx are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/) once 1.0.0 is out. Before that, a minor version may
change the API.

## [Unreleased]

## [0.1.0-rc7] - 2026-09-26

### Fixed

- An HTTP call is timed until its response body has been read, not until the status arrives.
  With Boot's default non-buffering request factories a 500ms download read as 5ms and the
  summary called it 1% of the transaction. `RestTemplate` and `RestClient` responses are
  wrapped and finish on close or end of body; `WebClient` finishes when the body terminates.

### Changed

- The URL in a report is scheme, host, port and path. The query string, user info and fragment
  are dropped before the report is built, so a token passed as a query parameter no longer
  reaches the log, the violation store or the `FAIL` exception message.

## [0.1.0-rc6] - 2026-09-26

### Fixed

- `Puretx.watch` inside a transaction uses the engine of the context that opened it, not the
  last context to start. In a suite with cached contexts, a `WARN` context's application code
  could throw because a `FAIL` context had started later, and record into the wrong store.
- In `FAIL` mode, a slow `REQUIRES_NEW` transaction inside a Spring test-managed transaction
  fails the test. The failure was deferred to the test transaction, which always rolls back and
  swallowed it.
- With two producer factories, a local Kafka transaction on one is reported while the other is
  in a Spring-managed transaction. The exemption now requires the bound `KafkaResourceHolder`
  to wrap this very producer.
- An ignore pattern naming a class also covers its nested and anonymous classes.

## [0.1.0-rc5] - 2026-09-26

### Fixed

- `KafkaTemplate.executeInTransaction` inside a database transaction is reported again. rc3
  exempted any producer that had seen `beginTransaction`, which also covered this local Kafka
  transaction that commits before the database does. The exemption now also requires the
  `KafkaResourceHolder` Spring binds when it ties a Kafka transaction to its own.
- A suspended Kafka transaction (`NOT_SUPPORTED` inside `KafkaTransactionManager`) is no longer
  reported when open-in-view keeps an entity manager bound. The probe now checks the manager's
  own resource key rather than whether any resource is bound.
- In `FAIL` mode, a slow inner `REQUIRES_NEW` transaction no longer rolls the outer transaction
  back around the committed inner one. The failure waits until the outermost transaction has
  committed, then surfaces.
- `ImpureTransactionException`'s message includes the call path.
- The startup warning about a missing transaction manager still lists what was instrumented.

### Changed

- `PuretxEngine.reportLongTransaction(TransactionInfo, long)` records and returns the violation
  instead of throwing; the framework hook decides whether the caller should fail.

## [0.1.0-rc4] - 2026-09-26

### Fixed

- A `RestTemplate` or `RestClient` call is timed until the response status arrives. With the
  default `HttpURLConnection` factory behind `new RestTemplate()` the request returns before the
  server has answered, so a 400ms call was reported as 9ms and the transaction summary blamed
  it for 2% of a transaction it had held for most of.

## [0.1.0-rc3] - 2026-09-26

Everything in here came from running rc2 on a real codebase.

### Fixed

- A transaction's length is now measured when it ends, not at the start of `beforeCommit`. The
  flush, `BEFORE_COMMIT` listeners and the commit itself were left out, which is exactly where a
  slow transaction is slow: a summary could say 812ms while the 500ms limit went unreported.
- Transactions run without synchronization are visible. `KafkaTransactionManager` defaults to
  `SYNCHRONIZATION_NEVER`, and puretx relied on the flag that setting never sets, so an HTTP call
  inside a Kafka transaction was never reported and the transaction was never timed.
- In `FAIL` mode a long transaction is reported after its commit rather than before it, so the
  test fails and the data stays. It also carries its duration now instead of `-1`, so it reaches
  the Micrometer timer.
- `ImpureTransactionException` no longer holds the live transaction object. Kept by a test
  report or an error tracker, it pinned the connection and persistence context behind it.
- A call refused in `FAIL` mode is no longer counted in the transaction summary as an external
  call that took 0ms.
- A `KafkaTemplate` built with configuration overrides no longer reports its transactional
  publishes. Spring Kafka copies the producer factory for such a template and binds the copy to
  the transaction, while puretx looked for the original; the producer's own transaction state is
  what is checked now, whichever factory made it.
- A `RestTemplate` bean built from `RestTemplateBuilder` is counted once in the startup report,
  not as both a `RestTemplate.Builder` and a `RestTemplate`.

## [0.1.0-rc2] - 2026-09-26

### Added

- Published to Maven Central as `io.github.ohchankyu:puretx-spring-boot-starter`. No extra
  repository is needed any more; the JitPack coordinate keeps working but is no longer documented.

### Changed

- Build tooling: Gradle 9.7, Lombok 1.18.48, feign-core 13.15, current GitHub Actions.

## [0.1.0-rc1] - 2026-09-25

First tagged version, served from JitPack.

### Added

- Detection of HTTP calls (`RestTemplate`, `RestClient`, `WebClient`, Feign) inside a Spring
  transaction. Clients are reached however they were built: as beans through a post-processor,
  or from an injected builder through Boot's customizers.
- Detection of message publishing inside a transaction, through any Kafka `ProducerFactory` and
  any `RabbitTemplate`. A Kafka transactional producer and a transacted Rabbit channel
  synchronised with the transaction are left alone.
- Detection of transactions held open past `puretx.max-duration`.
- `WARN` and `FAIL` modes, ignore patterns, application package hints and a bounded violation
  store for assertions.
- A one-line transaction summary saying how much of a transaction's life went to external calls.
- Micrometer meters for violations and for the external-call share of a transaction.
- `Puretx.watch` for calls puretx cannot instrument itself, and `Puretx.suppress` for calls that
  have been looked at and kept.
- A startup report of what was actually instrumented.
- Spring Boot 3.2 through 4.x on Java 17+. CI runs the suite against Boot 3.2, 3.5, 4.0 and 4.1
  on Java 17 and 21.
- JSpecify nullness annotations; `org.jspecify:jspecify` is an API dependency of `puretx-core`.
- `Automatic-Module-Name` in both jars: `io.github.ohchankyu.puretx` and
  `io.github.ohchankyu.puretx.spring`.

[Unreleased]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc7...HEAD
[0.1.0-rc7]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc6...v0.1.0-rc7
[0.1.0-rc6]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc5...v0.1.0-rc6
[0.1.0-rc5]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc4...v0.1.0-rc5
[0.1.0-rc4]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc3...v0.1.0-rc4
[0.1.0-rc3]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc2...v0.1.0-rc3
[0.1.0-rc2]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc1...v0.1.0-rc2
[0.1.0-rc1]: https://github.com/ohchanKyu/puretx/releases/tag/v0.1.0-rc1
