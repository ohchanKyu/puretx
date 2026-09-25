# Changelog

All notable changes to puretx are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/) once 1.0.0 is out. Before that, a minor version may
change the API.

## [Unreleased]

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

[Unreleased]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc1...HEAD
[0.1.0-rc1]: https://github.com/ohchanKyu/puretx/releases/tag/v0.1.0-rc1
