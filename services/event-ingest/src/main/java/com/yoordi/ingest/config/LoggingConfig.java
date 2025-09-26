package com.yoordi.ingest.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

@Configuration
public class LoggingConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceIdInterceptor());
    }

    public static class TraceIdInterceptor implements HandlerInterceptor {

        private static final String TRACE_ID_HEADER = "X-Trace-Id";
        private static final String REQUEST_ID_HEADER = "X-Request-Id";

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = request.getHeader(REQUEST_ID_HEADER);
            }
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString().substring(0, 8);
            }

            MDC.put("traceId", traceId);
            MDC.put("userId", request.getHeader("X-User-Id"));
            MDC.put("clientIp", getClientIpAddress(request));
            MDC.put("userAgent", request.getHeader("User-Agent"));
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());

            response.setHeader(TRACE_ID_HEADER, traceId);
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            MDC.clear();
        }

        private String getClientIpAddress(HttpServletRequest request) {
            String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
            };

            for (String header : headers) {
                String value = request.getHeader(header);
                if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                    return value.split(",")[0].trim();
                }
            }
            return request.getRemoteAddr();
        }
    }
}