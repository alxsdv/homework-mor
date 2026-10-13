package org.example.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Logs every API request twice: its method and path when it starts, then its status, duration and response body
 * when it finishes.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    // Not named logger: that would hide the commons-logging field OncePerRequestFilter inherits.
    private static final Logger requestLogger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** API docs and health checks are not API endpoints, and the spec body is large. */
    private static final List<String> SKIPPED_PATH_PREFIXES = List.of("/v3/api-docs", "/swagger-ui", "/actuator");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return SKIPPED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String methodAndPath = request.getMethod() + " " + pathWithQuery(request);
        requestLogger.info("Request {}", methodAndPath);
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, cachingResponse);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            requestLogger.info("Response {} -> {} in {} ms: {}", methodAndPath, cachingResponse.getStatus(), millis,
                               body(cachingResponse.getContentAsByteArray()));
            cachingResponse.copyBodyToResponse();
        }
    }

    private static String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    /** Keeps a pretty-printed body on its log line; valid JSON has no raw line breaks inside strings. */
    private static String body(byte[] content) {
        if (content.length == 0) {
            return "(empty)";
        }
        return new String(content, StandardCharsets.UTF_8).replaceAll("\\s*\\R\\s*", " ");
    }
}
