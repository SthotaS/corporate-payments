package com.wex.payments.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestTracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String traceId = resolveTraceId(request);

        MDC.put(LoggingConstants.TRACE_ID, traceId);
        response.setHeader(LoggingConstants.TRACE_HEADER, traceId);

        log.debug("trace id resolved traceId={} source={}",
                traceId,
                StringUtils.hasText(request.getHeader(LoggingConstants.TRACE_HEADER)) ? "request-header" : "generated");

        log.info("request started method={} path={} query={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("request completed method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs);
            MDC.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incomingTraceId = request.getHeader(LoggingConstants.TRACE_HEADER);
        return StringUtils.hasText(incomingTraceId) ? incomingTraceId.trim() : UUID.randomUUID().toString();
    }
}
