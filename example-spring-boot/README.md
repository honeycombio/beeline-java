# example-spring-boot

add `application.properties` file and include dataset, service name, writekey.

```sh
# (Required) Dataset to send the Events/Spans to
honeycomb.beeline.dataset=beeline-java-dataset
# (Required) Give your application a name to identify the origin of your events
honeycomb.beeline.service-name=beeline-java-service-name
# (Required) Your Honeycomb account API key
honeycomb.beeline.write-key=<mykey>
```

from root, `mvn clean install -DskipTests`

from this directory, `mvn spring-boot:run`

or to run in debug mode:

`mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8080"`

Alternatively, run / debug in intellij

When running, `curl localhost:8080` and check out telemetry in Honeycomb
