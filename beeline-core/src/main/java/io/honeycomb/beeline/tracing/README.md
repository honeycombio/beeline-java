# Instrumenting calls

## List of contents
* [Apache HTTP Components (Synchronous)](#apache-synchronous)
* [Apache HTTP Components (Async)](#apache-async)
* [Jersey](#jersey)
* [Java HTTP client (Synchronous)](#synchronous)
* [Java HTTP client (Asynchronous)](#asynchronous)
* [Resteasy](#resteasy)
* [Dropwizard](#dropwizard)

## Client Calls
Adding honeycomb trace header can help you track calls from one system to the next. Each HTTP client framework will have a slightly different way to add the header. To have this done automatically for each request, you will typically create a custom Interceptor or Filter.

**Note:** *To keep the examples concise, they call this utility function.*
```java
private String generateHoneycombTraceHeader(Span span){
    HttpHeaderV1PropagationCodec codec = HttpHeaderV1PropagationCodec.getInstance(); // Ideally dependency injected
    Map<String, Object> fields = new HashMap();
    fields.put("user_id", 1123);
    fields.put("user_name", "alyssaphacker");
    PropagationContext context = new PropagationContext(span.getTraceId(), span.getParentSpanId(), "dataset", fields);
    return codec.encode(context).orElse("");
}
```
### Apache HTTP Components
#### Apache Synchronous
[Apache's HttpComponents Client](https://hc.apache.org/httpcomponents-client-4.5.x/index.html)

Example creating request
```java
// GET
// 1. Create HTTP client
CloseableHttpClient client = HttpClients.custom().setConnectionTimeToLive(30, TimeUnit.SECONDS).build();
// 2. Create HTTP Request using GET
HttpGet getRequest = new HttpGet("https://google.com");
// 3. Add Honeycomb trace header
getRequest.addHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span));
// 4. Execute HTTP request and get response
CloseableHttpResponse getResponse = client.execute(getRequest);
```
```java
// POST
// 1. Create HTTP Request using POST
HttpPost postRequest = new HttpPost("https://google.com");
// 2. Add Honeycomb trace header
postRequest.addHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span));
// Execute HTTP request and get response
CloseableHttpResponse postResponse = client.execute(postRequest);
```

Use the HttpClientPropagator and a custom RequestExecutioner to programmatically handle this.
#### Apache Async
[Apache's HttpComponents Async Client](https://hc.apache.org/httpcomponents-asyncclient-4.1.x/index.html)

Example creating request with Honeycomb Trace set. This example uses the returned future for processing.
```java
final HttpGet request = new HttpGet("http://google.com");
request.addHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span));
final Future<HttpResponse> responseFuture = client.execute(request, null, null);
final HttpResponse response = responseFuture.get();
// process response here
```

Example creating request with Honeycomb Trace set. This example uses a callback for processing.
```java
final HttpGet request = new HttpGet("http://google.com");
request.addHeader(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span));
client.execute(request, null, new FutureCallback<HttpResponse>() {
    @Override
    public void completed(final HttpResponse result) {
        System.out.println("Callback completed successfully");
        // Process response here.
    }
    @Override
    public void failed(final Exception ex) {
        System.out.println("Callback with error");
        ex.printStackTrace();
    }
    @Override
    public void cancelled() {
        System.out.println("Callback was cancelled");
    }
});
```

Use the HttpClientPropagator and a custom HttpRequestInterceptor (start trace) and HttpResponseInterceptor (end trace) to programmatically handle this.

### Jersey
[Eclipse Jersey](https://eclipse-ee4j.github.io/jersey/) This implements JAX-RS specification. Any client conforming to
JAX-RS specification should use the same API.

Example creating request with Honeycomb Trace set. The method can also be passed a class for automatic entity conversion to class `.get(MyClass.class)`.
```java
System.out.println("Setting up Jersey client");
final Client client = ClientBuilder.newClient();
final WebTarget target = client.target("https://google.com");
// Can use target to hit different sub-resources. This is useful if hitting multiple endpoints of a REST API
final WebTarget mapTarget = target.path("/maps");
final Response response = mapTarget.request(MediaType.APPLICATION_JSON_TYPE)
    .header(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span))
    .get();
```

To do this programmatically, implement ClientRequestFilter / ClientResponseFilter

### Java HTTP client
Java SDK added a built-in HTTP client. It was experimental in JDK 9 and became stable in JDK 11.

#### Synchronous
Example creating request with Honeycomb Trace set.
```java
HttpClient client = HttpClient.newBuilder().build();
HttpRequest request = HttpRequest.newBuilder()
    .uri(new URI("https://postman-echo.com/get"))
    .header(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span))
    .GET()
    .build();
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandler.asString())
// process response
```
#### Asynchronous
Example creating request with Honeycomb Trace set.
```java
HttpClient client = HttpClient.newBuilder().build();
HttpRequest request = HttpRequest.newBuilder()
    .uri(new URI("https://postman-echo.com/get"))
    .header(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span))
    .GET()
    .build();
CompletableFuture<HttpResponse<String>> future = client.sendAsync(request, HttpResponse.BodyHandler.asString());
HttpResponse<String> response = future.get();
// process response
```
### Resteasy
Resteasy supports both JAX-RS (Jersey) and Apache HttpClient implementation clients. Based on JAX-RS specification syntax.

Example creating request with Honeycomb Trace header set using default client (Jersey).
```java
Client client = ResteasyClientBuilder.newBuilder().build();
WebTarget target = client.target("http://google.com");
Invocation.Builder request = target.request();
request.header(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span))
Response response = request.get();
```
### Dropwizard
Dropwizard's HTTP client is a Jersey client that also uses Apache HttpClient. Based on JAX-RS specification syntax.

Example creating request with Honeycomb Trace header set.
```java
// `environment` is an instance of io.dropwizard.setup.Environment
// jerseyConfiguration is an instance of io.dropwizard.client.JerseyClientConfiguration
JerseyClientBuilder builder = new io.dropwizard.client.JerseyClientBuilder(environment);
client = builder.using(jerseyConfiguration).build("example");
// Once client is created, use JAX-RS API as normal
final WebTarget target = client.target("https://google.com").path("/maps");
final Response response = target.request(MediaType.APPLICATION_JSON_TYPE)
    .header(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER, generateHoneycombTraceHeader(span))
    .get();
```
