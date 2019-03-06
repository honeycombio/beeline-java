package io.honeycomb.beeline.spring.e2e;

import io.honeycomb.beeline.tracing.Beeline;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

@SpringBootConfiguration
@EnableAutoConfiguration
public class End2EndTestConfig {
    @Bean
    public End2EndTestController end2EndTestController(final RestTemplateBuilder builder, final Beeline beeline) {
        return new End2EndTestController(builder, beeline);
    }

    @Bean
    public PathPatternTestController pathPatternTestController() {
        return new PathPatternTestController();
    }

    @Bean
    public RejectingFilter rejectionFilter() {
        return new RejectingFilter();
    }

    /**
     * This filter is for a specific test case. It will reject requests before they reach the interceptor.
     */
    @Bean
    public FilterRegistrationBean<RejectingFilter> rejectingFilter(final RejectingFilter filter) {
        // SpringServletContainerInitializer
        final FilterRegistrationBean<RejectingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    private static class RejectingFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(final HttpServletRequest request,
                                        final HttpServletResponse response,
                                        final FilterChain filterChain) throws ServletException, IOException {
            if (URI.create(request.getRequestURI()).getPath().startsWith("/reject-request")) {
                response.sendError(401, "Rejected!");
            } else {
                filterChain.doFilter(request, response);
            }
        }
    }
}
