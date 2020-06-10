# Spring Boot starter for Beeline projects using Spring Sleuth
This component will autoconfigure Spring Sleuth to send Sleuth traces to Honeycomb via the Beeline.

## Quick start
1. Add [Spring Sleuth starter](https://spring.io/projects/spring-cloud-sleuth)
2. Add Beeline starter for Spring Sleuth

    Using Gradle
    ```
    implementation 'io.honeycomb.beeline:beeline-spring-boot-sleuth-starter:1.1.1'
    ```
    Using Maven
    ```xml
    <dependency>
        <groupId>io.honeycomb.beeline</groupId>
        <artifactId>beeline-spring-boot-sleuth-starter</artifactId>
        <version>1.1.1</version>
    </dependency>
    ```
3. Configure API key and dataset via application.properties
    ```
    honeycomb.beeline.writeKey=somekey
    honeycomb.beeline.dataset=dataset
    ```
## Sleuth sample usage
```java
@Component
public class SpannedService {
    private static final Logger LOG = LoggerFactory.getLogger(SpannedService.class);

    final Tracer tracer; // This is an instance of brave.Tracer (NOT Beeline/Honeycomb)

    public SpannedService(final Tracer tracer) {
        this.tracer = tracer;
    }

    @NewSpan(name="other")
    public void other() {
        Span newSpan = tracer.nextSpan().name("other-programmatic_access");
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(newSpan.start())) {
            LOG.info("I'm in the child span doing some cool work that needs its own span");
            third();
        } finally {
            newSpan.finish();
        }

        LOG.info("I'm in the original span");
    }

    @ContinueSpan(log="third")
    public void third() {
        LOG.info("Third span");
    }
}
```
