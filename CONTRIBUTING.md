# Contributing

Thanks for looking. puretx is small on purpose, and the bar for a change is "does this help
someone find work that should not be inside a transaction, without ever crying wolf".

## Building

```
./gradlew build
```

That compiles against the oldest supported Spring Boot, runs checkstyle with zero warnings
allowed, and runs every test against the current Boot release. To run the tests against another
Boot version, which is what CI does for each supported line:

```
./gradlew build -PspringBootVersion=4.1.1
```

Java 17 or newer. The sample application runs with `./gradlew :puretx-sample:run`.

## What a good change looks like

- **A fix comes with a test that fails without it.** Say so in the pull request. A test that
  passes either way is not a regression test.
- **A new detection comes with its false-positive tests first.** The list in the README under
  "The false positives it will not raise" is the contract. If a new detector could fire on any
  of those, it is not ready.
- **Nothing proxies or replaces an application bean** unless the bean is immutable and
  `mutate()` is the only way in, as with `RestClient` and `WebClient`. Transaction managers keep
  their identity; HTTP clients keep their type.
- **Explain decisions in javadoc, on the thing they concern.** The code has no inline comments
  that restate what it does; a reader who wants to know *why* looks at the type or method.
  Rationale about a past bug belongs in the commit message.
- **Checkstyle at zero.** The configuration is `config/checkstyle.xml`, Google style with a few
  adjustments. The CheckStyle-IDEA plugin pointed at that file shows the same thing the build
  fails on.

## Commit messages

The subject line reads `type: what changed`, lower case, no full stop, with `type` one of
`feat`, `fix`, `docs`, `build`, `ci`, `chore`, `test`. The body says why, in prose. Look at
`git log` for the shape.

## Reporting a false positive

That is the most useful report there is. Use the "False positive" issue template: it asks for
the log block puretx printed and the shape of the code, which is usually enough to reproduce.

## Releasing

Maintainers only. Add a section to `CHANGELOG.md` for the version, then push a tag:

```
git tag v0.2.0
git push origin v0.2.0
```

The release workflow runs the full Boot matrix, builds the artifacts under that version and opens
a GitHub release with the changelog section as its notes. JitPack serves the tag on its own.
