package io.smartmoney.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request that carries a valid identity-service token, with
 * the role the token names. SecurityConfig decides which role may do what.
 * Not a Spring bean on purpose: it is added to the security chain only, so it
 * never also runs as a plain servlet filter.
 */
public class AdminTokenFilter extends OncePerRequestFilter {

    private final AdminTokenVerifier verifier;

    public AdminTokenFilter(AdminTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            verifier.verify(header.substring(7)).ifPresent(verified -> {
                var authority = new SimpleGrantedAuthority("ROLE_" + verified.role());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(verified.userId(), null, List.of(authority)));
            });
        }
        chain.doFilter(request, response);
    }
}
