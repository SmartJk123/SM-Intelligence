package com.smi.identity_service.staff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

/**
 * Applies staff roles to identity-service's admin endpoints. SecurityConfig has
 * already required a PLATFORM_ADMIN token; this checks the role's permission for
 * the area the endpoint belongs to. Administrators with full access pass every check.
 */
@Configuration
public class StaffAccessInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private record Rule(String prefix, boolean writesOnly, List<String> anyOf) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("/api/v1/admin/staff/me", false, List.of()),
            new Rule("/api/v1/admin/staff", false, List.of(StaffPermissions.SETTINGS)),
            new Rule("/api/v1/admin/staff-roles", false, List.of(StaffPermissions.SETTINGS)),
            new Rule("/api/v1/admin/settings", true, List.of(StaffPermissions.SETTINGS)),
            new Rule("/api/v1/admin/users", false, List.of("customers")),
            new Rule("/api/v1/admin/audit-events", false, List.of("audit-logs")),
            new Rule("/api/v1/admin/notifications", false, List.of("notifications")),
            new Rule("/api/v1/admin/email-outbox", false, List.of("notifications")),
            new Rule("/api/v1/admin/email-templates", false, List.of("notifications")),
            // The Add customer form lists organisations too.
            new Rule("/api/organizations", true, List.of("organisations")),
            new Rule("/api/organizations", false, List.of("organisations", "customers")));

    private final StaffService staff;

    public StaffAccessInterceptor(StaffService staff) {
        this.staff = staff;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/admin/**", "/api/organizations", "/api/organizations/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        boolean write = !"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod());
        for (Rule rule : RULES) {
            if (!path.startsWith(rule.prefix()) || (rule.writesOnly() && !write)) {
                continue;
            }
            if (rule.anyOf().isEmpty()) {
                return true;
            }
            UUID admin = currentUser();
            for (String permission : rule.anyOf()) {
                if (admin != null && staff.can(admin, permission)) {
                    return true;
                }
            }
            staff.require(admin, rule.anyOf().getFirst());
            return false;
        }
        return true;
    }

    private static UUID currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UUID id ? id : null;
    }
}
