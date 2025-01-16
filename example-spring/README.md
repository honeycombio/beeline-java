# spring boot example app

To test local code, add this to the root `pom.xml`:

```xml
    <distributionManagement>
        <repository>
            <id>local</id>
            <url>file://${user.home}/.m2/repository</url>
        </repository>
    </distributionManagement>
```

Then at the root:

```sh
mvn clean install -DskipTests
mvn deploy -DskipTests
```

Then in this directory

```sh
# run the app
mvn spring-boot:run

# run in debug mode
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8080"
```

Hit the endpoint for a trace `curl localhost:8080`

Tip: To ensure you're using the local copy, consider changing to a higher version in all the `pom.xml` files e.g. `2.42.42`
