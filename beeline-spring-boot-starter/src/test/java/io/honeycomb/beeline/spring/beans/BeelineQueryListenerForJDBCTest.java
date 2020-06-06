package io.honeycomb.beeline.spring.beans;

import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.honeycomb.beeline.tracing.utils.TraceFieldConstants;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.StatementType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.honeycomb.beeline.spring.beans.BeelineQueryListenerForJDBC.CHILD_SPAN_KEY;
import static io.honeycomb.beeline.spring.beans.BeelineQueryListenerForJDBC.ROOT_SPAN_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class BeelineQueryListenerForJDBCTest {

    @Mock
    Beeline beeline;
    @Mock
    Span activeSpan;
    @Mock
    Span childSpan;
    @Mock
    Tracer tracer;
    BeelineQueryListenerForJDBC listener;
    ExecutionInfo executionInfo;

    @Before
    public void setUp() {
        listener = new BeelineQueryListenerForJDBC(beeline);
        when(beeline.getActiveSpan()).thenReturn(activeSpan);
        when(activeSpan.isNoop()).thenReturn(false);
        when(beeline.getTracer()).thenReturn(tracer);
        when(tracer.startChildSpan(anyString())).thenReturn(childSpan);
        when(beeline.startTrace(anyString(), any(PropagationContext.class), anyString())).thenReturn(activeSpan);
        executionInfo = new ExecutionInfo();
        executionInfo.setStatementType(StatementType.STATEMENT);
    }

    @Test
    public void GIVEN_beforeQuery_EXPECT_spansOnExecutionInfo() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));

        listener.beforeQuery(executionInfo, queryInfoList);

        final Span rootSpan = executionInfo.getCustomValue(ROOT_SPAN_KEY, Span.class);
        final List<?> childSpans = executionInfo.getCustomValue(CHILD_SPAN_KEY, List.class);
        assertThat(rootSpan).isNotNull();
        assertThat(childSpans).isNotNull().hasSize(1);
        verify(childSpan, times(1)).markStart();
    }

    @Test
    public void GIVEN_beforeQueryBatch_EXPECT_spansOnExecutionInfo() {
        final List<QueryInfo> queryInfoList = Arrays.asList(new QueryInfo("query1"), new QueryInfo("query2"));

        listener.beforeQuery(executionInfo, queryInfoList);

        final Span rootSpan = executionInfo.getCustomValue(ROOT_SPAN_KEY, Span.class);
        final List<?> childSpans = executionInfo.getCustomValue(CHILD_SPAN_KEY, List.class);
        assertThat(rootSpan).isNotNull();
        assertThat(childSpans).isNotNull().hasSize(2);
        verify(childSpan, times(2)).markStart();
    }

    @Test
    public void GIVEN_afterQuery_EXPECT_fieldsOnSpan() {
        executionInfo.setElapsedTime(0);
        executionInfo.setStatementType(StatementType.STATEMENT);
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(childSpan));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verifyAddedFieldsToSpan(1);
    }

    /**
     * This helper method checks for default fields being added to span. We re-use the child span mock object so each
     * of these fields should be hit based on the number of times it has been processed. If there are 2 queries, the
     * mock child span should have been processed twice.
     * @param multiplier the number of child spans that should have been processed
     */
    private void verifyAddedFieldsToSpan(final int multiplier) {
        verify(childSpan, times(multiplier)).addField(eq(TraceFieldConstants.DATABASE_QUERY_FIELD), any());
        verify(childSpan, times(multiplier)).addField(eq(TraceFieldConstants.DATABASE_QUERY_PARAMETERS_FIELD), any());
        verify(childSpan, times(multiplier)).addField(eq(TraceFieldConstants.DATABASE_CONNECTION_ID_FIELD), any());
        verify(childSpan, times(multiplier)).addField(eq(TraceFieldConstants.DATABASE_STATEMENT_TYPE_FIELD), any());
        verify(childSpan, times(multiplier)).addField(eq(TraceFieldConstants.DATABASE_IS_SUCCESS), any());
        verify(childSpan, times(multiplier)).addField(eq(TraceFieldConstants.DATABASE_IS_BATCH_FIELD), any());
    }

    @Test
    public void GIVEN_afterQueryWithDuration_EXPECT_durationOnSpan() {
        final long duration = 100L;
        executionInfo.setElapsedTime(duration);
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(childSpan));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        final Span rootSpan = executionInfo.getCustomValue(ROOT_SPAN_KEY, Span.class);
        final List<?> childSpans = executionInfo.getCustomValue(CHILD_SPAN_KEY, List.class);
        assertThat(rootSpan).isNotNull();
        assertThat(childSpans).isNotNull().hasSize(1);
        verify(childSpan, times(1)).addField(eq(TraceFieldConstants.DURATION_FIELD), anyLong());
        verifyAddedFieldsToSpan(1);
    }


    @Test
    public void GIVEN_afterQueryNoDuration_EXPECT_durationMissingOnSpan() {
        executionInfo.setElapsedTime(0);
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(childSpan));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verify(childSpan, times(0)).addField(eq(TraceFieldConstants.DURATION_FIELD), anyLong());
        verifyAddedFieldsToSpan(1);
    }

    @Test
    public void GIVEN_afterQueryBatch_EXPECT_batchSizeOnSpan() {
        executionInfo.setBatch(true);
        executionInfo.setBatchSize(2);
        final List<QueryInfo> queryInfoList = Arrays.asList(new QueryInfo("query1"), new QueryInfo("query2"));
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Arrays.asList(childSpan, childSpan));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verify(childSpan, times(2)).addField(eq(TraceFieldConstants.DATABASE_BATCH_SIZE_FIELD), eq(2));
        verifyAddedFieldsToSpan(2);

    }

    @Test
    public void GIVEN_afterQueryNoBatch_EXPECT_batchSizeMissingFromSpan() {
        executionInfo.setBatch(false);
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(childSpan));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verify(childSpan, times(0)).addField(eq(TraceFieldConstants.DATABASE_BATCH_SIZE_FIELD), anyLong());
        verifyAddedFieldsToSpan(1);

    }

    @Test
    public void GIVEN_afterQueryThrowable_EXPECT_errorMessageOnSpan() {
        final String errorMessage = "some message";
        final Throwable throwable = new Throwable(errorMessage);
        executionInfo.setThrowable(throwable);
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(childSpan));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verify(childSpan, times(1)).addField(eq(TraceFieldConstants.DATABASE_ERROR), eq("Throwable"));
        verify(childSpan, times(1)).addField(eq(TraceFieldConstants.DATABASE_ERROR_DETAILS), eq(errorMessage));
        verifyAddedFieldsToSpan(1);
    }

    @Test
    public void GIVEN_afterQueryMissingThrowable_EXPECT_errorMessageMissingOnSpan() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(childSpan));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verify(childSpan, times(0)).addField(eq(TraceFieldConstants.DATABASE_ERROR), eq("Throwable"));
        verify(childSpan, times(0)).addField(eq(TraceFieldConstants.DATABASE_ERROR_DETAILS), anyString());
        verifyAddedFieldsToSpan(1);
    }

    @Test
    public void GIVEN_afterQueryCouldNotConvertRootSpan_EXPECT_nothingToHappen() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        final Object nonSpan = new Object();
        executionInfo.addCustomValue(ROOT_SPAN_KEY, nonSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verifyNoInteractions(activeSpan, childSpan);
    }

    @Test
    public void GIVEN_afterQueryCouldNotConvertChildSpanList_EXPECT_invalidSpanDataDroppedButRootSpanClosed() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        final Object nonSpan = mock(Mock.class);
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);
        executionInfo.addCustomValue(CHILD_SPAN_KEY, nonSpan);

        listener.afterQuery(executionInfo, queryInfoList);

        verifyNoInteractions(nonSpan, childSpan);
        verify(activeSpan, atLeastOnce()).close();
    }
    @Test
    public void GIVEN_afterQueryCouldNotConvertChildSpan_EXPECT_invalidSpanDataDroppedButRootSpanClosed() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        final Object nonSpan = mock(Mock.class);
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(nonSpan));

        listener.afterQuery(executionInfo, queryInfoList);

        verifyNoInteractions(nonSpan, childSpan);
        verify(activeSpan, atLeastOnce()).close();
    }

    @Test
    public void GIVEN_afterQueryLessSpansThanQueries_EXPECT_onlyProcessNumberOfSpans() {
        final List<QueryInfo> queryInfoList = Arrays.asList(new QueryInfo("query1"), new QueryInfo("query2"));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Collections.singletonList(childSpan));

        listener.afterQuery(executionInfo, queryInfoList);

        verifyAddedFieldsToSpan(1);
    }

    @Test
    public void GIVEN_afterQueryMoreSpansThanQueries_EXPECT_onlyProcessNumberOfQueries() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);
        executionInfo.addCustomValue(CHILD_SPAN_KEY, Arrays.asList(childSpan, childSpan));

        listener.afterQuery(executionInfo, queryInfoList);

        verifyAddedFieldsToSpan(1);
    }

    @Test
    public void GIVEN_afterQueryMissingChildSpans_EXPECT_rootSpanToBeClosed() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, activeSpan);
        executionInfo.addCustomValue(CHILD_SPAN_KEY, null);

        listener.afterQuery(executionInfo, queryInfoList);

        verifyAddedFieldsToSpan(0);
        verify(activeSpan, atLeastOnce()).close();
    }

    @Test
    public void GIVEN_afterQueryMissingRootSpan_EXPECT_nothingToHappen() {
        final List<QueryInfo> queryInfoList = Collections.singletonList(new QueryInfo("query1"));
        executionInfo.addCustomValue(ROOT_SPAN_KEY, null);
        executionInfo.addCustomValue(CHILD_SPAN_KEY, null);

        listener.afterQuery(executionInfo, queryInfoList);

        verifyNoInteractions(activeSpan, childSpan);
    }
}
