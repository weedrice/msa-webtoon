package com.yoordi.ingest.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class PerformanceConfig implements WebMvcConfigurer {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("EventIngest-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PerformanceInterceptor());
    }

    public static class PerformanceInterceptor implements HandlerInterceptor {

        private static final ThreadLocal<Long> startTimeThreadLocal = new ThreadLocal<>();

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            startTimeThreadLocal.set(System.currentTimeMillis());
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            Long startTime = startTimeThreadLocal.get();
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;

                // Log slow requests (>1000ms)
                if (duration > 1000) {
                    org.slf4j.LoggerFactory.getLogger(PerformanceInterceptor.class)
                            .warn("Slow request detected: {} {} took {}ms",
                                    request.getMethod(), request.getRequestURI(), duration);
                }

                startTimeThreadLocal.remove();
            }
        }
    }

    // Custom metrics
    @Bean
    public Counter eventPublishCounter(MeterRegistry meterRegistry) {
        return Counter.builder("events.published.total")
                .description("Total number of events published")
                .register(meterRegistry);
    }

    @Bean
    public Counter eventPublishErrorCounter(MeterRegistry meterRegistry) {
        return Counter.builder("events.publish.errors.total")
                .description("Total number of event publish errors")
                .register(meterRegistry);
    }

    @Bean
    public Timer eventPublishTimer(MeterRegistry meterRegistry) {
        return Timer.builder("events.publish.duration")
                .description("Event publish duration")
                .register(meterRegistry);
    }

    @Bean
    public Counter httpRequestCounter(MeterRegistry meterRegistry) {
        return Counter.builder("http.requests.total")
                .description("Total HTTP requests")
                .register(meterRegistry);
    }
}