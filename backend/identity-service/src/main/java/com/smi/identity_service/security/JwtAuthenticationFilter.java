package com.smi.identity_service.security;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Turns a valid Bearer token into an authenticated request.
 *
 * The role is read from the database on every request rather than from the
 * token, so a suspension or a role change applies immediately instead of when
 * the token expires. A deleted or suspended account is left unauthenticated,
 * which the security rules then answer with 401.
 *
 * The principal is the user id, so a controller can tell who is acting.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            if (jwtService.isTokenValid(token)) {
                UUID userId = jwtService.extractUserId(token);
                userRepository.findById(userId)
                        .filter(user -> user.getDeletedAt() == null && !user.isSuspended())
                        .ifPresent(user -> authenticate(user));
            }
        }
        chain.doFilter(request, response);
    }

    private static void authenticate(User user) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
