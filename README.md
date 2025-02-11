# Honeycomb Beeline for Java

[![OSS Lifecycle](https://img.shields.io/osslifecycle/honeycombio/beeline-java?color=pink)](https://github.com/honeycombio/home/blob/main/honeycomb-oss-lifecycle-and-practices.md)
[![CircleCI](https://circleci.com/gh/honeycombio/beeline-java.svg?style=shield)](https://circleci.com/gh/honeycombio/beeline-java) [![Beeline Core](https://img.shields.io/maven-central/v/io.honeycomb.beeline/beeline-core.svg)](https://search.maven.org/search?q=a:beeline-core) [![Beeline Spring Boot Starter](https://img.shields.io/maven-central/v/io.honeycomb.beeline/beeline-spring-boot-starter.svg)](https://search.maven.org/search?q=a:beeline-spring-boot-starter)

⚠️**STATUS**: This project is being Sunset. See [this issue](https://github.com/honeycombio/beeline-java/issues/322) for more details.

⚠️**Note**: Beelines are Honeycomb's legacy instrumentation libraries. We embrace OpenTelemetry as the effective way to instrument applications. For any new observability efforts, we recommend [instrumenting with OpenTelemetry](https://docs.honeycomb.io/send-data/java/opentelemetry-agent/).

This package makes it easy to instrument your Java web application to send useful events to
[Honeycomb](https://www.honeycomb.io), a service for debugging your software in production.

- [Usage and Examples](https://docs.honeycomb.io/beeline/java/)
- [Javadoc](https://honeycombio.github.io/beeline-java/overview-summary.html)

## Dependencies

- Java 8+ for the `beeline-core` and `beeline-spring-boot-starter` modules.
- Spring Boot 2 for the `beeline-spring-boot-starter` module.

## Contributions

Features, bug fixes and other changes to `beeline-java` are gladly accepted.
Please open issues or a pull request with your change.
Remember to add your name to the CONTRIBUTORS file!

All contributions will be released under the Apache License 2.0.
