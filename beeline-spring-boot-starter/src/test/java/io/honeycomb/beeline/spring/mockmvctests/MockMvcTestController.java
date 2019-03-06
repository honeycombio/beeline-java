package io.honeycomb.beeline.spring.mockmvctests;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.spring.beans.aspects.ChildSpan;
import io.honeycomb.beeline.spring.beans.aspects.SpanField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@RestController
public class MockMvcTestController {
    @Autowired
    private Beeline beeline;

    @GetMapping("/prefix/**")
    public String prefix() {
        return "hello";
    }

    @GetMapping("/get-with-var/{pathVar}/etc")
    public String pathVar(@PathVariable("pathVar") final String pathVar) {
        beeline.getActiveSpan().addField("user-field", pathVar);
        return "hello";
    }

    @GetMapping("/basic-get")
    public String basicGet() {
        beeline.getActiveSpan().addField("user-field", "user-data");
        return "hello";
    }

    @GetMapping(value = "/basic-get", params = {"first-word", "second-word"})
    public String basicGetWithQueryParams(@RequestParam("first-word") final String firstWord,
                                          @RequestParam("second-word") final String secondWord) {
        final String concatenated = firstWord + secondWord;
        beeline.getActiveSpan().addField("concatenated", concatenated);
        return concatenated;
    }

    @GetMapping("/basic-get-async")
    public CompletableFuture<String> basicGetAsync() {
        beeline.getActiveSpan().addField("user-field", "user-data");
        return CompletableFuture.supplyAsync(() -> "hello");
    }

    @GetMapping("/basic-get-async-throws")
    public CompletableFuture<String> basicGetAsyncThrows() {
        beeline.getActiveSpan().addField("user-field", "user-data");
        return CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Oops");
        });
    }

    @GetMapping("/get-async-callable")
    public Callable<String> getAsyncCallable() {
        beeline.getActiveSpan().addField("user-field", "user-data");
        return () -> "hello";
    }

    @GetMapping("/get-async-deferred")
    public DeferredResult<String> getAsyncDeferred() {
        beeline.getActiveSpan().addField("user-field", "user-data");
        final DeferredResult<String> deferredResult = new DeferredResult<>();
        new Thread(() -> deferredResult.setResult("hello")).start();

        return deferredResult;
    }

    @PostMapping("/basic-post")
    public String basicPost(@RequestBody final String data) {
        beeline.getActiveSpan().addField("echo", "data");
        return "hello: " + data;
    }

    @GetMapping("/throw-unhandled")
    public void throwUnhandled() throws Exception {
        throw new Exception("Oops");
    }


    @GetMapping("/throw-handled")
    public void throwHandled() {
        throw new TestException("Oops!");
    }

    @GetMapping("/get-timeout")
    public Callable<String> timeout() {
        beeline.getActiveSpan().addField("user-field", "user-data");
        return () -> {
            Thread.sleep(10_000);
            return "nope!";
        };
    }

    @ChildSpan(name = "AnnotatedController", addResult = true)
    @GetMapping("/annotation-span")
    public String annotationSpan(@SpanField("app-header") @RequestHeader("x-application-header") final String applicationHeader) {
        beeline.getActiveSpan().addField("user-field", "insects");
        return "bacteria";
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleTestException(final TestException e) {
        beeline.getActiveSpan().addField("test-exception-message", e.getMessage());
    }

    public static class TestException extends RuntimeException {
        public TestException(final String message) {
            super(message);
        }
    }
}
