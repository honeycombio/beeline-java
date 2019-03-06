package io.honeycomb.beeline.spring.e2e;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.spring.beans.aspects.ChildSpan;
import io.honeycomb.beeline.spring.beans.aspects.SpanField;
import io.honeycomb.libhoney.utils.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Controller
public class End2EndTestController {
    private RestTemplate restTemplate;
    private Beeline beeline;

    @Autowired
    public End2EndTestController(final RestTemplateBuilder builder, final Beeline beeline) {
        this.restTemplate = builder.build();
        this.beeline = beeline;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Call through to backend
    ///////////////////////////////////////////////////////////////////////////
    @GetMapping("/call-backend")
    @ResponseBody
    public String sendRequestToMock() {
        final ResponseEntity<String> forEntity = restTemplate.getForEntity("http://localhost:8089/backend-service", String.class);
        Assert.isTrue(forEntity.getStatusCodeValue() == 200, "Must be 200 OK");
        return forEntity.getBody();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Simple forward
    ///////////////////////////////////////////////////////////////////////////
    @GetMapping("/forward-request")
    public String forward() {
        beeline.getActiveSpan().addField("step1", true);
        return "forward:/receive-forward";
    }

    @GetMapping("/receive-forward")
    public ResponseEntity<String> receiveForward() {
        beeline.getActiveSpan().addField("step2", true);
        return ResponseEntity.ok("Forward Received");
    }

    ///////////////////////////////////////////////////////////////////////////
    // Forward to async
    ///////////////////////////////////////////////////////////////////////////
    @GetMapping("/forward-to-async")
    public String forwardToAsync() {
        beeline.getActiveSpan().addField("step1", true);
        return "forward:/receive-forward-async";
    }

    @GetMapping("/receive-forward-async")
    @ResponseBody
    public CompletableFuture<String> receiveForwardAsync() {
        beeline.getActiveSpan().addField("step2", true);
        return CompletableFuture.supplyAsync(() -> "Forward Received Async");
    }
    ///////////////////////////////////////////////////////////////////////////
    // Forward to exception throwing endpoint
    ///////////////////////////////////////////////////////////////////////////
    @GetMapping("/forward-to-exception")
    public String forwardToException() {
        beeline.getActiveSpan().addField("step1", true);
        return "forward:/receive-forward-exception";
    }

    @GetMapping("/receive-forward-exception")
    public ResponseEntity<String> receiveForwardException() throws Exception {
        beeline.getActiveSpan().addField("step2", true);
        throw new Exception("Oh boi!");
    }


    ///////////////////////////////////////////////////////////////////////////
    // Forward to async endpoint completing with exception
    ///////////////////////////////////////////////////////////////////////////
    @GetMapping("/forward-to-exception-async")
    public String forwardToAsyncException() {
        beeline.getActiveSpan().addField("step1", true);
        return "forward:/receive-forward-exception-async";
    }

    @GetMapping("/receive-forward-exception-async")
    @ResponseBody
    public CompletableFuture<String> receiveForwardAsyncException() {
        beeline.getActiveSpan().addField("step2", true);
        return CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Oh dear!");
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Endpoint with long async task (for timeout test case)
    ///////////////////////////////////////////////////////////////////////////
    @GetMapping("/timeout-request")
    @ResponseBody
    public CompletableFuture<String> timeoutRequest() {
        beeline.getActiveSpan().addField("step2", true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "We don't want to see this response!";
        });
    }


    ///////////////////////////////////////////////////////////////////////////
    // Annotated endpoint
    ///////////////////////////////////////////////////////////////////////////

    @ChildSpan(name = "AnnotatedController", addResult = true)
    @GetMapping("/annotation-span")
    @ResponseBody
    public String annotationSpan(@SpanField("app-header") @RequestHeader("x-application-header") final String applicationHeader) {
        beeline.getActiveSpan().addField("user-field", "insects");
        return "bacteria";
    }
}
