# Security

puretx observes transactions and outbound calls inside one JVM. It opens no ports, makes no
network calls of its own, and stores nothing outside process memory. Its attack surface is what
it logs: a violation report contains the transaction name, the request method and URL, and
application stack frames. Query strings are part of the URL, so a secret passed as a query
parameter would reach the log. Do not pass secrets in query strings; that advice predates puretx.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting on this
repository ("Report a vulnerability" under the Security tab). You will get an acknowledgement
within a week, and a fix or a decision within thirty days.

## Supported versions

The latest release only. There is no backport policy before 1.0.
