package io.honeycomb.beeline.examples;

import io.honeycomb.beeline.tracing.Span;

public class DatabaseService {
    public DatabaseService(BeelineConfig hnyConfig) {
        this.hnyConfig = hnyConfig;
    }

    private BeelineConfig hnyConfig;

    public void queryDb(String id) {
        try (Span childSpan = hnyConfig.getBeeline().startChildSpan("customer-db-query")) {
            String data = getCustomerDataById(id);
            childSpan.addField("customer-data", data);
        }

    }

    public String getCustomerDataById(String id) {
        return "customer-0123";
    }
}
