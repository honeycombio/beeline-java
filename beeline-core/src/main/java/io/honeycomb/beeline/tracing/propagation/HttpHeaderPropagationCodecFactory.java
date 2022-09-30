package io.honeycomb.beeline.tracing.propagation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpHeaderPropagationCodecFactory {

    /**
     * Factory method to create a HTTP header {@link PropagationCodec} from a list
     * of codec names.
     * <p>
     * Returns a {@link HttpHeaderPropagationCodecFactory} if no valid codec names
     * are provided.
     * </p>
     * Returns a {@link CompositeHttpHeaderPropagator} if more than one valid codec
     * is provided.
     *
     * @param propagatorNames the named of the codecs to use
     * @return a propagtion codec to be used to parse and propagate trace data.
     */
    public static PropagationCodec<Map<String, String>> create(final List<String> propagatorNames) {
        if (propagatorNames == null || propagatorNames.isEmpty()) {
            return Propagation.defaultHeader();
        }

        List<PropagationCodec<Map<String, String>>> codecs = new ArrayList<>();
        for (String codecName : propagatorNames) {
            switch (codecName) {
                case AWSPropagationCodec.CODEC_NAME:
                    codecs.add(Propagation.aws());
                    break;
                case HttpHeaderV1PropagationCodec.CODEC_NAME:
                    codecs.add(Propagation.honeycombHeaderV1());
                    break;
                case W3CPropagationCodec.CODEC_NAME:
                    codecs.add(Propagation.w3c());
                    break;
                case DefaultPropagationCodec.CODEC_NAME:
                    codecs.add(Propagation.defaultHeader());
                    break;
                default:
                    continue;
            }
        }

        switch (codecs.size()) {
            case 0:
                // if no codecs, default to (honeycomb or w3c, honeycomb takes precedence)
                return Propagation.defaultHeader();
            case 1:
                // if only one codec, return it directly
                return codecs.get(0);
            default:
                // multiple codecs, return composite propagator
                return new CompositeHttpHeaderPropagator(codecs);
        }
    }
}
