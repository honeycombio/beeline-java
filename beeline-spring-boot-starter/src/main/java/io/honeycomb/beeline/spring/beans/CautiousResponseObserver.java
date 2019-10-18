package io.honeycomb.beeline.spring.beans;

import io.honeycomb.libhoney.responses.ServerRejected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * A Response Observer that LOGs an ERROR if a 401 occurred, and otherwise logs at DEBUG or TRACE level.
 */
public class CautiousResponseObserver extends DebugResponseObserver {
    private static final Logger LOG = LoggerFactory.getLogger(CautiousResponseObserver.class);

    @Override
    protected void handle401(ServerRejected serverRejected) {
        LOG.error(ERROR_TEMPLATE_401, serverRejected);
    }
}
