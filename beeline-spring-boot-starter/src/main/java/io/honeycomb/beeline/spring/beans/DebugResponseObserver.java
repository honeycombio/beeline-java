package io.honeycomb.beeline.spring.beans;

import io.honeycomb.libhoney.ResponseObserver;
import io.honeycomb.libhoney.responses.ClientRejected;
import io.honeycomb.libhoney.responses.ServerAccepted;
import io.honeycomb.libhoney.responses.ServerRejected;
import io.honeycomb.libhoney.responses.Unknown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugResponseObserver implements ResponseObserver {
    private static final Logger LOG = LoggerFactory.getLogger(DebugResponseObserver.class);

    @Override
    public void onServerAccepted(final ServerAccepted serverAccepted) {
        LOG.trace("Event successfully sent to Honeycomb: {}", serverAccepted);
    }

    @Override
    public void onServerRejected(final ServerRejected serverRejected) {
        LOG.debug("Event rejected by Honeycomb server: {}", serverRejected);
    }

    @Override
    public void onClientRejected(final ClientRejected clientRejected) {
        LOG.debug("Event rejected on the client side: {}", clientRejected);
    }

    @Override
    public void onUnknown(final Unknown unknown) {
        LOG.debug("Received an unknown error while trying to send Event to Honeycomb: {}", unknown);
    }
}
