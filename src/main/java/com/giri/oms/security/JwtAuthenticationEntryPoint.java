package com.giri.oms.security;

import com.giri.oms.common.exception.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Invoked by Spring Security whenever an unauthenticated request hits an
 * endpoint that requires authentication — i.e. what would otherwise be a
 * bare 403 with no body becomes the same ErrorResponse JSON shape the rest
 * of the API already returns for 404s, 409s, etc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper objectMapper;
    private final Clock clock;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        log.warn("Unauthenticated request rejected — path: {}, reason: {}", request.getRequestURI(), authException.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(clock),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                com.giri.oms.common.exception.ErrorCode.UNAUTHENTICATED.code(),
                com.giri.oms.common.exception.ErrorCode.UNAUTHENTICATED.formatMessage(),
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
