package io.honeycomb.beeline.spring.beans;

import io.honeycomb.libhoney.ResponseObserver;
import io.honeycomb.libhoney.responses.ClientRejected;
import io.honeycomb.libhoney.responses.ServerAccepted;
import io.honeycomb.libhoney.responses.ServerRejected;
import io.honeycomb.libhoney.responses.Unknown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class DebugResponseObserver implements ResponseObserver {
    private static final Logger LOG = LoggerFactory.getLogger(DebugResponseObserver.class);

    protected static final String ERROR_TEMPLATE_401 = "Server responded with a 401 HTTP error code to a batch request." +
        " This is likely caused by using an incorrect 'Team Write Key'. Check https://ui.honeycomb.io/account to verify your " +
        "team write key. Rejected event: {}";

    @Override
    public void onServerAccepted(final ServerAccepted serverAccepted) {
        LOG.trace("Event successfully sent to Honeycomb: {}", serverAccepted);
    }

    @Override
    public void onServerRejected(final ServerRejected serverRejected) {
        if (serverRejected.getBatchData().getBatchStatusCode() == HttpStatus.UNAUTHORIZED.value()) {
            handle401(serverRejected);
        } else {
            LOG.debug("Event rejected by Honeycomb server: {}", serverRejected);
        }
    }

    @Override
    public void onClientRejected(final ClientRejected clientRejected) {
        LOG.debug("Event rejected on the client side: {}", clientRejected);
    }

    @Override
    public void onUnknown(final Unknown unknown) {
        LOG.debug("Received an unknown error while trying to send Event to Honeycomb: {}", unknown);
    }

    protected void handle401(final ServerRejected serverRejected) {
        LOG.debug(ERROR_TEMPLATE_401, serverRejected);
    }
}
