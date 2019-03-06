package io.honeycomb.beeline.spring.beans.aspects;

import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.Tracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@ImportAutoConfiguration(AopAutoConfiguration.class) // we need AOP configured for this to work
@RunWith(SpringRunner.class)
public class SpanAspectTest {

    @Configuration
    public static class Config {
        @Bean
        public SpanAspect spanAspect(final Tracer tracer) {
            return new SpanAspect(tracer);
        }

        @Bean
        public AnnotationTester annotationTester() {
            return new AnnotationTester();
        }
    }

    @Autowired
    private SpanAspect spanAspect;

    @Autowired
    private AnnotationTester annotationTester;

    @MockBean
    private Tracer tracer;

    private Span mockSpan;

    @Before
    public void setUp() {
        mockSpan = mock(Span.class);
        when(mockSpan.addField(anyString(), anyString())).thenReturn(mockSpan);
        when(tracer.startChildSpan(anyString())).thenReturn(mockSpan);
    }

    /**
     * This is a stubbed out class that we use to test the implementation of our Aspect.
     * We wire it into the Spring context (see Config above) so that it gets processed by the AOP magic.
     */
    public static class AnnotationTester {
        @ChildSpan
        public void plainMethod() {

        }

        @ChildSpan("ExplicitlyNamed")
        public void methodWithExplicitSpanName() {

        }

        @ChildSpan(name = "AlsoExplicitlyNamed")
        public void methodWithNameOnAnnotationAlias() {

        }

        @ChildSpan(addResult = true)
        public Map<String, String> methodWithAddResult() {
            return Collections.singletonMap("key", "value");
        }

        @ChildSpan(addResult = true)
        public void voidMethodWithAddResult() {

        }

        @ChildSpan
        public void methodWithParameter(final String param0) {

        }

        @ChildSpan
        public void methodWithAnnotatedParameters(final String param0,
                                                  @SpanField final String param1,
                                                  @SpanField final String param2) {

        }

        @ChildSpan
        public void methodWithNamedParameter(final String param0,
                                             @SpanField final String param1,
                                             @SpanField("Third-Parameter") final String param2) {

        }

        @ChildSpan
        public void methodThrowingException(final String param0,
                                             @SpanField final String param1,
                                             @SpanField("Third-Parameter") final String param2) {
            throw new RuntimeException("Oops!");
        }
    }

    @Test
    public void EXPECT_aspectToHaveExpectedInstrumentationName() {
        assertThat(spanAspect.getName()).isEqualTo("spring_aop");
    }

    @Test
    public void GIVEN_testObjectIsAnnotated_EXPECT_testObjectToBeAnAOPProxy() {
        assertThat(AopUtils.isAopProxy(annotationTester)).isTrue();
    }

    @Test
    public void GIVEN_plainlyAnnotatedMethod_WHEN_callingMethod_EXPECT_spanToUseMethodNameAsSpanName() {
        annotationTester.plainMethod();

        verify(tracer).startChildSpan("PlainMethod");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.plainMethod()");
        verify(mockSpan).close();

        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedMethodWithValueSet_WHEN_callingMethod_EXPECT_spanToUseAnnotationValueAsSpanName() {
        annotationTester.methodWithExplicitSpanName();

        verify(tracer).startChildSpan("ExplicitlyNamed");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.methodWithExplicitSpanName()");
        verify(mockSpan).close();

        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedMethodWithNameParameterSet_WHEN_callingMethod_EXPECT_spanToUseAnnotationNameValueAsSpanName() {
        annotationTester.methodWithNameOnAnnotationAlias();

        verify(tracer).startChildSpan("AlsoExplicitlyNamed");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.methodWithNameOnAnnotationAlias()");
        verify(mockSpan).close();

        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedMethodWithAddResult_WHEN_callingMethod_EXPECT_resultToBeAddedAsField() {
        annotationTester.methodWithAddResult();

        verify(tracer).startChildSpan("MethodWithAddResult");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.methodWithAddResult()");
        verify(mockSpan).addField(eq("spring.method.result"), eq(Collections.singletonMap("key", "value")));
        verify(mockSpan).close();

        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedVoidMethodWithAddResultSet_WHEN_callingMethod_EXPECT_spanToNotAddResultField() {
        annotationTester.voidMethodWithAddResult();

        verify(tracer).startChildSpan("VoidMethodWithAddResult");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.voidMethodWithAddResult()");
        verify(mockSpan).close();

        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedMethodWithPlainParameter_WHEN_callingMethod_EXPECT_noFieldForParameterToBeAdded() {
        annotationTester.methodWithParameter("paramZero");

        verify(tracer).startChildSpan("MethodWithParameter");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.methodWithParameter(..)");
        verify(mockSpan).close();

        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedMethodWithAnnotatedParameter_WHEN_callingMethod_EXPECT_parameterFieldsToBeAdded() {
        annotationTester.methodWithAnnotatedParameters("paramZero", "paramOne", "paramTwo");

        verify(tracer).startChildSpan("MethodWithAnnotatedParameters");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.methodWithAnnotatedParameters(..)");

        // We can't safely assert on what exactly the suffix is because it is dependent on the
        // "-parameters" compiler option, see the javadoc on determineParameterFieldName()
        verify(mockSpan).addField(matches("spring\\.method\\.param\\.(1|param1)"), eq("paramOne"));
        verify(mockSpan).addField(matches("spring\\.method\\.param\\.(2|param2)"), eq("paramTwo"));
        verify(mockSpan).close();
        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedMethodWithNamedAnnotatedParameter_WHEN_callingMethod_EXPECT_parameterFieldsToBeAdded() {
        annotationTester.methodWithNamedParameter("paramZero", "paramOne", "paramTwo");

        verify(tracer).startChildSpan("MethodWithNamedParameter");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.methodWithNamedParameter(..)");
        verify(mockSpan).close();

        // We can't safely assert on what exactly the suffix is because it is dependent on the
        // "-parameters" compiler option, see the javadoc on determineParameterFieldName()
        verify(mockSpan).addField(matches("spring\\.method\\.param\\.(1|param1)"), eq("paramOne"));
        verify(mockSpan).addField("Third-Parameter", "paramTwo");
        verifyNoMoreInteractions(mockSpan, tracer);
    }

    @Test
    public void GIVEN_annotatedMethodThatThrowsException_WHEN_callingMethod_EXPECT_errorParametersToBeAdded() {
        assertThatThrownBy(() -> annotationTester.methodThrowingException("paramZero", "paramOne", "paramTwo"))
            .isInstanceOf(RuntimeException.class);

        verify(tracer).startChildSpan("MethodThrowingException");
        verify(mockSpan).addField("type", "annotated_method");
        verify(mockSpan).addField("spring.method.name", "AnnotationTester.methodThrowingException(..)");

        // We can't safely assert on what exactly the suffix is because it is dependent on the
        // "-parameters" compiler option, see the javadoc on determineParameterFieldName()
        verify(mockSpan).addField(matches("spring\\.method\\.param\\.(1|param1)"), eq("paramOne"));
        verify(mockSpan).addField("Third-Parameter", "paramTwo");
        verify(mockSpan).addField("spring.method.error", "RuntimeException");
        verify(mockSpan).addField("spring.method.error_detail", "Oops!");
        verify(mockSpan).close();

        verifyNoMoreInteractions(mockSpan, tracer);
    }
}
