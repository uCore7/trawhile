package com.trawhile.adapter.inbound.observability;

import brave.Tracing;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Brave tracing to the Micrometer {@link io.micrometer.observation.ObservationRegistry}
 * so that every HTTP request observation carries {@code traceId} and {@code spanId} in MDC.
 *
 * <p>In Spring Boot 4.x, the Brave tracing auto-configuration that existed in
 * Spring Boot 3.x's {@code spring-boot-actuator-autoconfigure} was removed. This
 * configuration class provides the minimum wiring needed by SR-00-C16.F01:
 * <ol>
 *   <li>Brave's {@link CurrentTraceContext} is constructed with
 *       {@link MDCScopeDecorator} so that opening a Brave scope propagates
 *       {@code traceId} and {@code spanId} into SLF4J MDC.</li>
 *   <li>A {@link BraveTracer} wraps the Brave {@link Tracing}.</li>
 *   <li>An {@link ObservationRegistryCustomizer} registers the tracing handler
 *       after the {@link io.micrometer.observation.ObservationRegistry} is created,
 *       avoiding the circular dependency that would arise from directly injecting
 *       the registry into a {@code @Bean} factory method.</li>
 * </ol>
 * </p>
 *
 * <p>All beans are conditional on absence of a pre-existing {@link Tracer} bean so
 * that production deployments that supply their own tracing back-end remain compatible.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ Tracing.class, BraveTracer.class })
public class TracingMdcConfig {

    /**
     * Brave {@link CurrentTraceContext} with SLF4J MDC decoration.
     * {@link MDCScopeDecorator} copies Brave's {@code traceId}/{@code spanId} into MDC
     * whenever a Brave scope is opened (i.e., when a span is started or an existing
     * context is propagated into a new thread/filter).
     */
    @Bean
    @ConditionalOnMissingBean(CurrentTraceContext.class)
    public CurrentTraceContext braveCurrentTraceContext() {
        return ThreadLocalCurrentTraceContext.newBuilder()
                .addScopeDecorator(MDCScopeDecorator.get())
                .build();
    }

    /**
     * Brave {@link Tracing} instance using the MDC-aware current trace context.
     * Brave is configured with a no-op reporter since there is no distributed tracing
     * backend in this deployment. Spans are created for MDC correlation only.
     */
    @Bean
    @ConditionalOnMissingBean(Tracing.class)
    public Tracing braveTracing(CurrentTraceContext currentTraceContext) {
        return Tracing.newBuilder()
                .currentTraceContext(currentTraceContext)
                .build();
    }

    /**
     * Micrometer {@link Tracer} backed by the local Brave instance.
     */
    @Bean
    @ConditionalOnMissingBean(Tracer.class)
    public Tracer braveTracer(Tracing tracing) {
        return new BraveTracer(
                tracing.tracer(),
                new BraveCurrentTraceContext(tracing.currentTraceContext()),
                new BraveBaggageManager());
    }

    /**
     * Exposes the {@link DefaultTracingObservationHandler} as a bean so Spring
     * Boot 4.x's {@code ObservationRegistryPostProcessor} picks it up from its
     * {@code ObjectProvider<ObservationHandler<?>>} and registers it through
     * {@code ObservationHandlerGroups} — the idiomatic path that respects
     * group ordering. (Registering it via {@code ObservationRegistryCustomizer}
     * works mechanically but bypasses the handler-group registration step that
     * runs first, leaving tracing handlers ungrouped and unreliable.)
     */
    @Bean
    @ConditionalOnMissingBean(DefaultTracingObservationHandler.class)
    public DefaultTracingObservationHandler tracingObservationHandler(Tracer tracer) {
        return new DefaultTracingObservationHandler(tracer);
    }
}
