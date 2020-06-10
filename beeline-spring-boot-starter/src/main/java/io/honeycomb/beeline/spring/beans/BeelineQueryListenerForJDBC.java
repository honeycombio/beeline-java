package io.honeycomb.beeline.spring.beans;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BeelineQueryListenerForJDBC implements QueryExecutionListener {
    final static String CHILD_SPAN_KEY = "childSpans";
    final static String ROOT_SPAN_KEY = "rootSpan";
    private static final Logger LOG = LoggerFactory.getLogger(BeelineQueryListenerForJDBC.class);
    private final static String BATCH_SPAN_NAME = "query_batch";
    private final static String QUERY_SPAN_NAME = "query";
    private final static String SERVICE_NAME = "query_listener";

    private final Beeline beeline;

    public BeelineQueryListenerForJDBC(final Beeline beeline) {
        this.beeline = beeline;
    }

    @Override
    public void beforeQuery(final ExecutionInfo execInfo, final List<QueryInfo> queryInfoList) {
        final Span rootSpan;
        if(beeline.getActiveSpan().isNoop()){
            rootSpan = beeline.startTrace(BATCH_SPAN_NAME, PropagationContext.emptyContext(), SERVICE_NAME);
        } else {
            rootSpan = beeline.getActiveSpan();
        }
        final Tracer tracer = beeline.getTracer();
        final List<Span> childSpans = new ArrayList<>();
        queryInfoList.forEach(queryInfo -> {
            final Span childSpan = tracer.startChildSpan(QUERY_SPAN_NAME);
            childSpan.markStart();
            childSpans.add(childSpan);
        });
        execInfo.addCustomValue(CHILD_SPAN_KEY, childSpans);
        execInfo.addCustomValue(ROOT_SPAN_KEY, rootSpan);
    }

    @SuppressFBWarnings({"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", "NP_LOAD_OF_KNOWN_NULL_VALUE"}) // JDK 11 issue https://github.com/spotbugs/spotbugs/issues/756
    @Override
    public void afterQuery(final ExecutionInfo execInfo, final List<QueryInfo> queryInfoList) {
        try (Span rootSpan = safelyValidateRootSpan(execInfo)) {
            final List<Span> childSpans = safelyValidateChildSpans(execInfo);
            if (rootSpan == null) {
                LOG.error("Root span not found");
                return;
            }
            if (childSpans == null) {
                LOG.error("Child spans not found");
                return;
            }
            if (childSpans.size() != queryInfoList.size()) {
                LOG.warn("Expected Child spans to match queries childSpans={} queries={}", childSpans.size(), queryInfoList.size());
            }

            final int length = Math.min(childSpans.size(), queryInfoList.size());
            for (int i = 0; i < length; i++) {
                final Object o = childSpans.get(i);
                final Class<?> oClass = o.getClass();
                if (!Span.class.isAssignableFrom(oClass)) {
                    LOG.warn("Expected span type but got class {}", oClass);
                    continue;
                }
                try (Span span = (Span) o) {
                    final QueryInfo info = queryInfoList.get(i);
                    applyToSpan(execInfo, info, span);
                }
            }
        }
    }

    private Span safelyValidateRootSpan(ExecutionInfo executionInfo) {
        try {
            return executionInfo.getCustomValue(ROOT_SPAN_KEY, Span.class);
        } catch (ClassCastException e) {
            LOG.error("Root span invalid type");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Span> safelyValidateChildSpans(ExecutionInfo executionInfo) {
        try {
            return executionInfo.getCustomValue(CHILD_SPAN_KEY, List.class);

        } catch (ClassCastException e) {
            LOG.error("Child span invalid type");
        }
        return null;
    }

    private void applyToSpan(final ExecutionInfo execInfo, final QueryInfo queryInfo, final Span span) {
        final long duration = execInfo.getElapsedTime();
        final boolean isBatch = execInfo.isBatch();
        final Throwable throwable = execInfo.getThrowable();
        span.addField(TraceFieldConstants.DATABASE_QUERY_FIELD, queryInfo.getQuery());
        span.addField(TraceFieldConstants.DATABASE_QUERY_PARAMETERS_FIELD, queryInfo.getQueryArgsList());
        span.addField(TraceFieldConstants.DATABASE_CONNECTION_ID_FIELD, execInfo.getConnectionId());
        span.addField(TraceFieldConstants.DATABASE_STATEMENT_TYPE_FIELD, execInfo.getStatementType().name());
        span.addField(TraceFieldConstants.DATABASE_IS_SUCCESS, execInfo.isSuccess());
        span.addField(TraceFieldConstants.DATABASE_IS_BATCH_FIELD, isBatch);
        if (duration > 0) {
            span.addField(TraceFieldConstants.DURATION_FIELD, duration);
        }
        if (isBatch) {
            span.addField(TraceFieldConstants.DATABASE_BATCH_SIZE_FIELD, execInfo.getBatchSize());
        }
        if (throwable != null) {
            span.addField(TraceFieldConstants.DATABASE_ERROR, throwable.getClass().getSimpleName());
            span.addField(TraceFieldConstants.DATABASE_ERROR_DETAILS, throwable.getMessage());
        }
    }
}
