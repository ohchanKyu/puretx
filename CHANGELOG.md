# Changelog

All notable changes to puretx are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/) once 1.0.0 is out. Before that, a minor version may
change the API.

## [Unreleased]

### Added

- RabbitMQ detection: a publish through any `RabbitTemplate` bean inside a transaction is
  reported, unless the template's transacted channel is synchronised with that transaction.
- Spring Boot 4 support. The HTTP client customizers moved packages in Boot 4; puretx now
  declares each one for both Boot 3 and Boot 4 and uses whichever is on the classpath.
- A `RestClientCustomizer`, so a `RestClient` built from the injected `RestClient.Builder` inside
  a constructor is instrumented. That is the pattern the Spring Boot reference guide recommends,
  and it never produces a bean for the post-processor to see.
- Continuous integration runs the test suite against Spring Boot 3.2, 3.5, 4.0 and 4.1 on
  Java 17 and 21.
- Javadoc jars, POM metadata and optional artifact signing, everything a Maven Central release
  needs.
- `Automatic-Module-Name` in both jars: `io.github.ohchankyu.puretx` and
  `io.github.ohchankyu.puretx.spring`.

### Changed

- `spring-boot-autoconfigure` is a runtime dependency of the starter in the published POM rather
  than a compile-scope one.
- The `RestClient` and `WebClient` post-processors leave a client that already carries the
  interceptor untouched, instead of rebuilding it and counting it a second time in the startup
  report.

## [0.1.0-rc1]

First tagged version, served from JitPack.

### Added

- Detection of HTTP calls (`RestTemplate`, `RestClient`, `WebClient`, Feign) and Kafka publishing
  inside a Spring transaction, and of transactions held open past a threshold.
- `WARN` and `FAIL` modes, ignore patterns, application package hints and a bounded violation
  store for assertions.
- A one-line transaction summary saying how much of a transaction's life went to external calls.
- Micrometer meters for violations and for the external-call share of a transaction.
- `Puretx.watch` for calls puretx cannot instrument itself, and `Puretx.suppress` for calls that
  have been looked at and kept.
- A startup report of what was actually instrumented.

[Unreleased]: https://github.com/ohchanKyu/puretx/compare/v0.1.0-rc1...HEAD
[0.1.0-rc1]: https://github.com/ohchanKyu/puretx/releases/tag/v0.1.0-rc1
