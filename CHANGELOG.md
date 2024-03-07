# Beeline Changelog

## [2.2.0] - 2024-03-07

### Enhancements

- feat: support classic ingest keys (#311) | @cewkrupa

### Maintenance

- Give dependabot PRs better title (#264) | @JamieDanielson
- Update validate PR title workflow (#259) | @pkanal
- Validate PR title (#257) | @pkanal
- Move nightly to weekly (#299) | @vreynolds
- Update readme (#297) | @vreynolds
- Add otel recommendation to readme (#288) | @pkanal
- Update codeowners to pipeline-team (#310) | @JamieDanielson
- Update codeowners to pipeline (#309) | @JamieDanielson
- Ignore spring boot 3 upgrade (#283) | @vreynolds
- Ignore mockito 5 updates (#273) | @vreynolds
- Delete workflows for old board (#253) | @vreynolds
- Add release file (#252) | @vreynolds
- Add new project workflow (#251) | @vreynolds
- Bump pmdCoreVersion from 6.49.0 to 6.51.0 (#254)
- Bump maven-source-plugin from 3.2.1 to 3.3.0 (#296)
- Bump maven-surefire-plugin from 2.22.2 to 3.1.2 (#298)
- Bump maven-gpg-plugin from 3.0.1 to 3.1.0 (#295)
- Bump maven-pmd-plugin from 3.20.0 to 3.21.0 (#293)
- Bump pmdCoreVersion from 6.51.0 to 6.55.0 (#290)
- Bump maven-compiler-plugin from 3.10.1 to 3.11.0 (#282)
- Bump examples/httpclient from 4.5.13 to 4.5.14 (#292)
- Bump maven-pmd-plugin from 3.19.0 to 3.20.0 (#291)
- Bump datasource-proxy from 1.8 to 1.8.1 (#279)
- Bump byte-buddy from 1.14.0 to 1.14.3 (#276)
- Bump libhoney-java from 1.5.3 to 1.5.4 (#275)
- Bump byte-buddy from 1.12.17 to 1.14.0 (#271)
- Bump rest-assured from 5.2.0 to 5.3.0 (#280)
- Bump assertj-core from 3.23.1 to 3.24.2 (#284)
- Bump slf4j-simple from 2.0.3 to 2.0.7 (#277)
- Bump httpclient5 from 5.1.3 to 5.2.1 (#274)

## [2.1.1] - 2022-09-30

### Maintenance

- Fix javadoc warnings (#247) | [@MikeGoldsmith](https://github.com/MikeGoldsmith)
- Add Java 18 to CI matrix (#229) | [@vreynolds](https://github.com/vreynolds)
- Bump libhoney-java from 1.5.0 to 1.5.3 (#248)
- Bump maven-jxr-plugin from 3.2.0 to 3.3.0 (#245)
- Bump mockito-core from 4.6.1 to 4.8.0 (#244)
- Bump maven-javadoc-plugin from 3.4.0 to 3.4.1 (#243)
- Bump slf4j-simple from 1.7.36 to 2.0.3 (#242)
- Bump maven-pmd-plugin from 3.16.0 to 3.19.0 (#239)
- Bump rest-assured from 5.1.1 to 5.2.0 (#240)
- Bump byte-buddy from 1.12.10 to 1.12.17 (#238)
- Bump maven-jar-plugin from 3.2.2 to 3.3.0 (#237)
- Bump pmdCoreVersion from 6.47.0 to 6.49.0 (#236)
- Bump maven-project-info-reports-plugin from 3.3.0 to 3.4.1 (#233)
- Bump datasource-proxy from 1.7 to 1.8 (#225)
- Bump mockito-core from 4.6.0 to 4.6.1 (#228)
- Bump pmdCoreVersion from 6.46.0 to 6.47.0 (#224)
- Bump rest-assured from 5.1.0 to 5.1.1 (#222)
- Bump pmdCoreVersion from 6.45.0 to 6.46.0 (#218)
- Bump rest-assured from 5.0.1 to 5.1.0 (#220)
- Bump assertj-core from 3.22.0 to 3.23.1 (#221)
- Bump mockito-core from 4.5.1 to 4.6.0 (#219)

## [2.1.0] - 2022-05-04

### Enhancements

- Add meta.span_type to root/subroot spans (#203) | [@MikeGoldsmith](https://github.com/MikeGoldsmith)

### Maintenance

- add basic example using DefaultBeeline (#197) | [@JamieDanielson](https://github.com/JamieDanielson)
- Bump javax.servlet-api from 3.1.0 to 4.0.1 (#114)
- Bump mockito-core from 4.4.0 to 4.5.1 (#209)
- Bump rest-assured from 4.4.0 to 5.0.1 (#215)
- Bump nexus-staging-maven-plugin from 1.6.8 to 1.6.13 (#214)
- Bump pmdCoreVersion from 6.41.0 to 6.45.0 (#212)
- Bump maven-pmd-plugin from 3.15.0 to 3.16.0 (#213)
- Bump maven-jxr-plugin from 3.1.1 to 3.2.0 (#211)
- Bump maven-javadoc-plugin from 3.3.1 to 3.4.0 (#210)
- Bump libhoney-java from 1.3.1 to 1.5.0 (#208)
- Bump slf4j-simple from 1.7.32 to 1.7.36 (#207)
- Bump maven-jar-plugin from 3.2.0 to 3.2.2 (#206)
- Bump maven-project-info-reports-plugin from 2.9 to 3.3.0 (#204)
- Bump assertj-core from 3.21.0 to 3.22.0 (#205)
- Bump httpclient from 4.5.9 to 4.5.13 (#200)
- Bump maven-compiler-plugin from 3.8.1 to 3.10.1 (#194)
- Bump httpclient from 4.5.9 to 4.5.13 in /examples (#198)
- Bump mockito-core from 4.2.0 to 4.4.0 (#195)

## [2.0.0] - 2022-03-21

### !!! Breaking Changes !!!

The beeline will no longer propagate `dataset` configuration across distributed traces. To ensure distributed traces continue to send data to the appropriate dataset either:
1. [Recommended] Configure a `dataset` in every instrumented application (e.g. using the `honeycomb.beeline.dataset` property). Most users configure their applications this way already, so you may not need to do anything.
2. Alternatively, configure a [custom trace propagation hook](https://docs.honeycomb.io/getting-data-in/beeline/java/#custom-trace-propagation-hook) to inject dataset in the outgoing request header, and a [custom trace parser hook](https://docs.honeycomb.io/getting-data-in/beeline/java/#custom-trace-parser-hook) to extract dataset from the incoming request header.

### Enhancements

- Add support for Environment & Services (#187) | [@MikeGoldsmith](https://github.com/MikeGoldsmith)
- add service.name field in addition to service_name (#188) | [@JamieDanielson](https://github.com/JamieDanielson)
- fix: only trim service name for dataset (#189) | [@JamieDanielson](https://github.com/JamieDanielson)

### Maintenance

- Bump maven-jxr-plugin from 2.5 to 3.1.1 (#126) | [dependabot](https://github.com/dependabot)
- gh: add re-triage workflow (#174) | [@vreynolds](https://github.com/vreynolds)
- Bump mockito-core from 4.1.0 to 4.2.0 (#177) | [dependabot](https://github.com/dependabot)
- update releasing.md for current temporary issue (#178) | [@JamieDanielson](https://github.com/JamieDanielson)
- update maven orb to handle multi-module project (#179) | [@JamieDanielson](https://github.com/JamieDanielson)
- merge releases-1.x.x into main (#193) | [@vreynolds](https://github.com/vreynolds)

## [1.7.1] - 2022-03-18

### Fixed

-  fix circular dependency for spring boot 2.6.x (#190) (#191) | [@giorgimode](https://github.com/giorgimode)

## [1.7.0] - 2021-12-23

### Improvements

- feat: parse both w3c and honeycomb propagation headers by default (#170) | [@JamieDanielson](https://github.com/JamieDanielson)

### Maintenance

- Bump pmdCoreVersion from 6.40.0 to 6.41.0 (#169) | [dependabot](https://github.com/dependabot)
- Update dependabot.yml (#168) | [@vreynolds](https://github.com/vreynolds)
- Bump pmdCoreVersion from 6.39.0 to 6.40.0 (#163) | [dependabot](https://github.com/dependabot)
- Bump mockito-core from 4.0.0 to 4.1.0 (#167) | [dependabot](https://github.com/dependabot)
- Bump spotbugs-maven-plugin from 4.4.2.1 to 4.5.0.0 (#166) | [dependabot](https://github.com/dependabot)
- empower apply-labels action to apply labels (#164) | [Robb Kidd](https://github.com/)
- ci: add java 17 to test matrix (#161) | [@vreynolds](https://github.com/vreynolds)
- Bump mockito-core from 3.12.4 to 4.0.0 (#159) | [dependabot](https://github.com/dependabot)
- Bump spotbugs-maven-plugin from 4.4.1 to 4.4.2.1 (#160) | [dependabot](https://github.com/dependabot)
- Bump pmdCoreVersion from 6.38.0 to 6.39.0 (#156) | [dependabot](https://github.com/dependabot)
- ci: fix meven release (#158) | [@vreynolds](https://github.com/vreynolds)

## [1.6.1] - 2021-09-27

### Added

- Improve end trace logging (#148) | [@big-andy-coates](https://github.com/big-andy-coates)

### Maintenance

- Change maintenance badge to maintained (#153)
- Adds Stalebot (#154)
- Add issue and PR templates (#144)
- Add OSS lifecycle badge (#143)
- Updates Github Action Workflows (#135)
- Updates Dependabot Config (#134)
- Switches CODEOWNERS to telemetry-team (#133)
- Bump maven-javadoc-plugin from 3.2.0 to 3.3.1 (#142, #151)
- Bump maven-gpg-plugin from 1.6 to 3.0.1 (#141)
- Bump slf4j-simple from 1.7.30 to 1.7.32 (#140)
- Bump mockito-core from 3.3.3 to 3.12.4 (#137, #145)
- Bump spotbugs-maven-plugin from 4.2.0 to 4.4.1 (#136, #152)
- Bump maven from 0.6.0 to 0.7.7 (#123)
- Bump assertj-core from 3.19.0 to 3.21.0 (#132, #149)
- Bump rest-assured from 3.3.0 to 4.4.0 (#128)
- Bump maven-surefire-plugin from 2.22.1 to 2.22.2 (#122)
- Bump maven-pmd-plugin from 3.9.0 to 3.15.0 (#120, #150)
- Bump pmdCoreVersion from 6.32.0 to 6.38.0 (#119, #138, #147)

## 1.6.0

### Changes

- Replace black/white with allow/deny lists (#109)

### Maintenance

- Bump maven-compiler-plugin from 3.7.0 to 3.8.1 (#116)
- Bump assertj-core from 3.11.1 to 3.19.0 (#115)
- Bump maven-jar-plugin from 3.1.1 to 3.2.0 (#117)
- Bump pmdCoreVersion from 6.30.0 to 6.32.0 (#113)
- Bump wiremock from 2.26.0 to 2.27.2 (#100)
- Bump slf4j-simple from 1.7.25 to 1.7.30 (#101)
- Bump maven-gpg-plugin from 1.5 to 1.6 (#112)
- Bump junit from 4.13.1 to 4.13.2 (#111)
- Bump nexus-staging-maven-plugin from 1.6.7 to 1.6.8 (#110)
- Bump maven-source-plugin from 3.0.1 to 3.2.1 (#98)
- Bump datasource-proxy from 1.6 to 1.7 (#104)
- Bump spotbugsVersion from 4.0.0-beta4 to 4.2.2 (#108)
- Bump libhoney-java from 1.3.0 to 1.3.1 (#99)

## 1.5.1

### Fixes:

- Default w3c sampled flag to 01 so the OTEL collector does not ignore the spans (#105)
- Change Brave span transforming to properly associate spans (#95)
- Suppress spotbug warning for volatile array (#96)

### Maintenance

- Add dependabot config (#97)
- Use public CircleCI context for build secrets (#94)
- Bump spotbugs-maven-plugin from 3.1.12.2 to 4.2.0 (#103)
- Bump pmdCoreVersion from 6.3.0 to 6.30.0 (#102)
- Bump junit from 4.12 to 4.13.1 (#83)

## 1.5.0

Improvements:

- Use brave span's trace and span ID as parent context (#85)
- Allow span duration to be set directly (#84)
- Add tracestate parsing / propagation to W3C codec (#78)
- Use HoneyClientBuilder.apiHost URI overload  (#80)

Maintenance:

- Rework build, add matrix and use orb, fix tag filters (#90)
- add .vscode to gitignore (#86)

## 1.4.0

- Update CircleCI to publish to maven when creating tag (#74)
- Add support for configuring a proxy via spring boot AutoConf settings (#77)

## 1.3.0

- Tidy up pom files (#73)
- Remove spring boot starter bin dir (#72)
- Bump maven-javadoc-plugin to v3.2.0 (#71)
- Add parse and propagation hooks for HttpClientPropagator and HttpServerPropagator (#65)
- Add config metadata JSON for spring boot for better IDE intellisense (#66)
- Ensure AutoConfig happens before Sleuth is invoked (#68)
- Use secure random in W3C trace ID generator for improved spread distribution (#62)
- Use 'hny' for honeycomb propagation codec name (#64)
- Simplify deterministic sampler tests to improve consistnecy (#63)
- Add support for configuring multiple propagators via config (#61)
- Add composite HTTP header propagator (#56)
- Refactor AWS propagator codec to prefer self over parent when decoding (#60)
- Add nightly builds (#58)
- Add codewoners file (#57)
- Update links to other repos to use main instead of master branch (#54)
- Refactor propagator codec implementations to use map<string, string> for encode & decode (#53)

## 1.2.0

This is a big release! New functionality:

- JPA / JDBC Autoinstrumentation! Spring Boot 2 users will now get spans automatically emitted as part of a trace for JDBC queries including details about their database queries.
- Spring Boot Sleuth integration. See the associated [README](https://github.com/honeycombio/beeline-java/tree/main/beeline-spring-boot-sleuth-starter) for more information.
- Simplified Beeline Builder that gives more access to transport options like proxy configuration. See [README](https://github.com/honeycombio/beeline-java/blob/main/beeline-core/src/main/java/io/honeycomb/beeline/builder/README.md).
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
