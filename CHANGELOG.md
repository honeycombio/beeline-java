# Beeline Changelog

## 1.0.8

Improvements:

- Updates libhoney-java dependency to [1.1.1](https://github.com/honeycombio/libhoney-java/releases/tag/v1.1.1)
- Adds high-level utilities to make manual instrumentation of HTTP clients/servers easier, and refactors the Spring starter's ServletFilter and RestTemplate Interceptor to use these.
- Migrates the servlet filter from the Spring Starter module into the Core module so that non-Spring apps can utilize this functionality without relying on the additional Spring Boot dependencies.
- Adds a DebugResponseObserver by default to quickly expose any errors that occur when sending your data to Honeycomb.

Fixes:

- Fixes a versioning issue in the parent POM: the POM was referencing an old version of Core.
- Fixes am issue around exceptions thrown in the servlet filter chain. Before, the response status might have been labeled incorrectly. Now, the response status field is no longer added when there's a possibility it could be incorrect. This case occurs typically when the user has not handled an exception, e.g. by using @ExceptionHandler in Spring Boot.
