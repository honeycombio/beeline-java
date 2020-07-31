package io.honeycomb.beeline.tracing.propagation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CompositeHttpHeaderPropagtor implements PropagationCodec<Map<String, String>> {

    private final List<PropagationCodec<Map<String, String>>> codecs;

    public CompositeHttpHeaderPropagtor(List<PropagationCodec<Map<String, String>>> codecs) {
        if (codecs == null || codecs.size() == 0) {
            throw new IllegalArgumentException();
        }

        this.codecs = codecs;
    }

    @Override
    public PropagationContext decode(Map<String, String> encodedTrace) {
        for (PropagationCodec<Map<String, String>> codec : codecs) {
            PropagationContext context = codec.decode(encodedTrace);

            // return first context that is not empty
            if (context != PropagationContext.emptyContext()) {
                return context;
            }
        }

        return PropagationContext.emptyContext();
    }

    @Override
    public Optional<Map<String, String>> encode(PropagationContext context) {
        Map<String, String> headers = new HashMap<>();
        for (PropagationCodec<Map<String, String>> codec : codecs) {
            Optional<Map<String, String>> codecheaders = codec.encode(context);
            if (codecheaders.isPresent()) {
                headers.putAll(codecheaders.get());
            }
        }

        // if no headrs were created, return nothing
        if (headers.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(headers);
    }
}
