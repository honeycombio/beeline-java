# Beeline Changelog

## 1.2.0

This is a big release! New functionality:

- JPA / JDBC Autoinstrumentation! Spring Boot 2 users will now get spans automatically emitted as part of a trace for JDBC queries including details about their database queries.
- Spring Boot Sleuth integration. See the associated [README](https://github.com/honeycombio/beeline-java/tree/master/beeline-spring-boot-sleuth-starter) for more information.
- Simplified Beeline Builder that gives more access to transport options like proxy configuration. See [README](https://github.com/honeycombio/beeline-java/blob/master/beeline-core/src/main/java/io/honeycomb/beeline/builder/README.md).
- Marshal / Unmarshal functions for W3C Trace Context and AWS Trace Context headers.
- The Beeline now generates 8-byte SpanIDs and 16-byte TraceIDs as hex encoded strings instead of v4 UUIDs.


## 1.1.0

Improvements: 

- Update wiremock version to 2.26.0.
- Make TracingContex an interface for holding span stack. This allows more flexibility in how related spans are tracked, especially for systems that use fixed-size thread pools that recycle threads.

## 1.0.9

Improvements:

- Change spotbugs dependency to have test scope to resolve [#26](https://github.com/honeycombio/beeline-java/issues/26)

## 1.0.8

Improvements:

- Updates libhoney-java dependency to [1.1.1](https://github.com/honeycombio/libhoney-java/releases/tag/v1.1.1)
- Adds high-level utilities to make manual instrumentation of HTTP clients/servers easier, and refactors the Spring starter's ServletFilter and RestTemplate Interceptor to use these.
- Migrates the servlet filter from the Spring Starter module into the Core module so that non-Spring apps can utilize this functionality without relying on the additional Spring Boot dependencies.
- Adds a DebugResponseObserver by default to quickly expose any errors that occur when sending your data to Honeycomb.

Fixes:

- Fixes a versioning issue in the parent POM: the POM was referencing an old version of Core.
- Fixes am issue around exceptions thrown in the servlet filter chain. Before, the response status might have been labeled incorrectly. Now, the response status field is no longer added when there's a possibility it could be incorrect. This case occurs typically when the user has not handled an exception, e.g. by using @ExceptionHandler in Spring Boot.

## 1.0.7

Improvements:

- Updates libhoney dependency to [1.1.0](https://github.com/honeycombio/libhoney-java/releases/tag/v1.1.0).

## 1.0.6

Improvements:

- Supports transport override options in DefaultBeeline instance.

## 1.0.5

Improvements:

- Updates libhoney dependency to [1.0.9](https://github.com/honeycombio/libhoney-java/releases/tag/v1.0.9), which includes a proxy transport option to access the Honeycomb API through a proxy server.

## 1.0.4

Improvements:

- Adds DefaultBeeline singleton which allows initialization of the beeline with less up-front plumbing, assuming users are OK with defaults. Advanced users can still use the old initialization methods. [Click here](https://docs.honeycomb.io/getting-data-in/java/beeline/#installation-with-any-java-application-simple) for docs.

## 1.0.3

Security Updates:

- Updates libhoney dependency to [1.0.8](https://github.com/honeycombio/libhoney-java/releases/tag/v1.0.8).

## 1.0.2

Bugfixes:

- Fixes missing child spans when deterministic head sampling is turned on, by ensuring the same traceId is used for sampling and downstream generated spans (#9).

## 1.0.1

Improvements:

- Updates libhoney dependency to [1.0.7](https://github.com/honeycombio/libhoney-java/releases/tag/v1.0.7), which adds slf4j as a normal dependency to avoid version conflicts.

## 1.0.0

Initial release of Honeycomb Beeline for Java
