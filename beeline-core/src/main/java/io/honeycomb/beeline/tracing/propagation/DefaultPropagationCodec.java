package io.honeycomb.beeline.tracing.propagation;

import java.util.Map;
import java.util.Optional;

public class DefaultPropagationCodec implements PropagationCodec<Map<String, String>> {

    private static final DefaultPropagationCodec INSTANCE = new DefaultPropagationCodec();
    protected static final String CODEC_NAME = "default";

    public static DefaultPropagationCodec getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the codec name.
     */
    public String getName() {
        return CODEC_NAME;
    }

    @Override
    public PropagationContext decode(Map<String, String> headers) {
        PropagationContext w3cContext = W3CPropagationCodec.getInstance().decode(headers);
        PropagationContext honeycombContext = HttpHeaderV1PropagationCodec.getInstance().decode(headers);

        if (!honeycombContext.equals(PropagationContext.emptyContext())) {
            return honeycombContext;
        } else {
            return w3cContext;
        }
    }

    @Override
    public Optional<Map<String, String>> encode(PropagationContext context) {
        return HttpHeaderV1PropagationCodec.getInstance().encode(context);
    }

}
