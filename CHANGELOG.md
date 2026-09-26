# Changelog

All notable changes to puretx are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/) once 1.0.0 is out. Before that, a minor version may
change the API.

## [Unreleased]

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

[Unreleased]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc2...HEAD
[0.1.0-rc2]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc1...v0.1.0-rc2
[0.1.0-rc1]: https://github.com/ohchanKyu/puretx/releases/tag/v0.1.0-rc1
