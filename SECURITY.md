# Security

puretx observes transactions and outbound calls inside one JVM. It opens no ports, makes no
network calls of its own, and stores nothing outside process memory. Its attack surface is what
it logs: a violation report contains the transaction name, the request method, the scheme, host,
port and path of the URL, and application stack frames. The query string, user info and fragment
are dropped before the report is built, so a token passed as a query parameter does not reach the
log, the violation store or the `FAIL` exception message. Request bodies and headers are never
read.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting on this
repository ("Report a vulnerability" under the Security tab). You will get an acknowledgement
within a week, and a fix or a decision within thirty days.

## Supported versions

The latest release only. There is no backport policy before 1.0.
