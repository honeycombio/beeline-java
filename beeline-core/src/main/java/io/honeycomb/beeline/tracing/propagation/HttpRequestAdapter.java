package io.honeycomb.beeline.tracing.propagation;

import java.util.Map;

public interface HttpRequestAdapter {

    public Map<String, String> getHeaders();

}
