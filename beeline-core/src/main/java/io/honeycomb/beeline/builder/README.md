# Building Beelines with the BeelineBuilder
BeelineBuilder is an attempt to create a unified API for Beelines to simplify user configuration between the Beeline and the HoneyClient.

## Directly building Beelines
BeelineBuilder follows the Builder pattern. So create an instance of the builder, configure the options you need, and then call `build()`.

```java
package com.example.MyApp;

import io.honeycomb.beeline.DefaultBeeline;
import io.honeycomb.beeline.builder.BeelineBuilder;
import io.honeycomb.beeline.tracing.Beeline;

public class MyApp{
    /// ...
    public static void main( final String[] args ) {
        final BeelineBuilder builder = new BeelineBuilder();
        // Configure builder
        builder.dataSet("test-dataset").writeKey("WRITE_KEY");
        // Build beeline
        final Beeline beeline = builder.build();

        // ...

        // Call close at program termination to ensure all pending
        // spans are sent.
        beeline.close();
    }
}
```

## Using DefaultBeeline
DefaultBeeline has a new `getInstance` method which takes BeelineBuilder and serviceName.

```java
package com.example.MyApp;

import io.honeycomb.beeline.DefaultBeeline;
import io.honeycomb.beeline.builder.BeelineBuilder;

public class MyApp{
    /// ...
    public static void main( final String[] args ) {
        final BeelineBuilder builder = new BeelineBuilder();
        builder.dataSet("test-dataset").writeKey("WRITE_KEY");
        final DefaultBeeline beeline = DefaultBeeline.getInstance(builder, "my-app");

        // ...

        // Call close at program termination to ensure all pending
        // spans are sent.
        beeline.close();
    }
}
```

## Simplified Proxy Configuration
It is now much simpler for people to configure servers behind an http proxy. To add a proxy server that does not need authentication, simply point to the proxy service.
```java
builder.addProxyNoCredentials("https://myproxy.example.com");
```

If your proxy server needs authentication, you will need to pass in credentials like:
```java
builder.addProxyCredential("https://myproxy.example.com", "user", "secret");
```
