./mvnw -rf :beeline-examples exec:java -Dexec.mainClass="io.honeycomb.beeline.examples.ExampleApp"
curl -H "customer-id: 1" localhost:8080


If you set `-Dio.honeycomb.beeline.write-key=[...] -Dio.honeycomb.beeline.dataset=[...]` then you'll write to prod honeycomb!

