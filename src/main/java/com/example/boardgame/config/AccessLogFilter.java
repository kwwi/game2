package com.example.boardgame.config;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs incoming REST API requests with status and elapsed time.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            String path = (query == null || query.isEmpty()) ? uri : (uri + "?" + query);
            int status = response.getStatus();
            String ip = resolveClientIp(request);
            boolean sseRequest = isSseRequest(request, uri);

            // SSE endpoints are long-lived and can be very noisy in reconnect scenarios.
            // Keep only non-2xx logs for SSE requests.
            if (sseRequest && status < 400) {
                return;
            }

            String message = "HTTP {} {} -> {} ({} ms, ip={})";
            if (status >= 500) {
                log.error(message, method, path, status, elapsedMs, ip);
            } else if (status >= 400) {
                log.warn(message, method, path, status, elapsedMs, ip);
            } else {
                log.info(message, method, path, status, elapsedMs, ip);
            }
        }
    }

    private boolean isSseRequest(HttpServletRequest request, String uri) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return true;
        }
        return uri != null && uri.contains("/events");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

