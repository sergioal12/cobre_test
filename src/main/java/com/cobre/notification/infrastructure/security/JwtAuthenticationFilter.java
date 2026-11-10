package com.cobre.notification.infrastructure.security;

import com.cobre.notification.domain.exception.UnauthorizedClientAccessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <jwt>}. The authenticated principal is the
 * {@code client_id} claim, which becomes the ONLY source of tenant identity for the API (client-supplied
 * headers or parameters are never trusted to identify the caller).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final RestAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, RestAuthenticationEntryPoint entryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.entryPoint = entryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && !header.isBlank()) {
            String trimmed = header.trim();
            if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response,
                        new BadCredentialsException("Authorization header must use the 'Bearer' scheme"));
                return;
            }
            try {
                JwtTokenService.ValidatedToken validated = jwtTokenService.validateToken(trimmed.substring(7).trim());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        validated.clientId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
                ));
            } catch (UnauthorizedClientAccessException ex) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response, new BadCredentialsException(ex.getMessage()));
                return;
            }
        }

        chain.doFilter(request, response);
    }
}

