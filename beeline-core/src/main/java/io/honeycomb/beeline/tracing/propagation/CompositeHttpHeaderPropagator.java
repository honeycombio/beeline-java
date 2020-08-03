package io.honeycomb.beeline.tracing.propagation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Codec that encompasses one or more codec implementations and applies each codec on
 * each call to encode and decode.
 *
 * <h1>Thread-safety</h1>
 * Instances of this class are thread-safe and can be shared.
 */
public class CompositeHttpHeaderPropagator implements PropagationCodec<Map<String, String>> {

    private final List<PropagationCodec<Map<String, String>>> codecs;

    public CompositeHttpHeaderPropagator(List<PropagationCodec<Map<String, String>>> codecs) {
        if (codecs == null || codecs.size() == 0) {
            throw new IllegalArgumentException();
        }

        this.codecs = codecs;
    }

    /**
     * Calls each inner Decode and returns the first {@link PropagationContext}.
     *
     * @param encodedTrace to decode into a {@link PropagationContext}.
     * @return extracted context - "empty" context if encodedTrace value has an invalid format or is null.
     */
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

    /**
     * Calls each inner codec Encode and combines the returned HTTP headers.
     * <p>
     * Duplicate headers are overwritten by later codecs.
     * </p>
     *
     * @param context to encode into a valid header value.
     * @return a valid AWS http header value - empty if required IDs are missing or input is null.
     */
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
