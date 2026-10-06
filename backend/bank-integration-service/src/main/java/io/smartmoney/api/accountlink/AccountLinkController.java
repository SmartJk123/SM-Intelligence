package io.smartmoney.api.accountlink;

import io.smartmoney.api.config.SecurityConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for assigning bank accounts to customers. Behind the admin
 * token like everything under /api/v1/admin.
 *
 *   GET    /api/v1/admin/account-links?userId=
 *   POST   /api/v1/admin/account-links          {bankId, accountNumber, userId, accountName}
 *   POST   /api/v1/admin/account-links/{id}/sync
 *   DELETE /api/v1/admin/account-links/{id}
 */
@RestController
@RequestMapping("/api/v1/admin/account-links")
public class AccountLinkController {

    public record LinkRequest(String bankId, String accountNumber, String userId, String accountName,
                              String accountId) {
    }

    public record LinkView(Long id, String bankId, String accountNumber, String userId, String accountId,
                           String accountName, Instant createdAt, int pendingDeliveries, String lastError) {
    }

    private final AccountLinkService service;
    private final PlatformServicesClient platform;

    public AccountLinkController(AccountLinkService service, PlatformServicesClient platform) {
        this.service = service;
        this.platform = platform;
    }

    private static boolean isCustomer(Authentication caller) {
        return caller != null && caller.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + SecurityConfig.CUSTOMER_ROLE).equals(authority.getAuthority()));
    }

    @GetMapping
    public List<LinkView> list(@RequestParam(required = false) String userId) {
        return service.list(userId).stream().map(this::view).toList();
    }

    /**
     * An admin may link any account. A signed-in customer (the web app, right
     * after onboarding) may only link an account accounts-service already holds
     * for them at that bank and number; otherwise anyone could route a
     * stranger's bank notifications to their own dashboard.
     */
    @PostMapping
    public ResponseEntity<LinkView> link(@RequestBody LinkRequest request, Authentication caller) {
        if (isCustomer(caller)) {
            String self = String.valueOf(caller.getPrincipal());
            String institution = AccountLinkService.INSTITUTIONS.get(
                    request.bankId() == null ? "" : request.bankId().trim().toLowerCase());
            boolean own = self.equals(request.userId())
                    && request.accountId() != null && !request.accountId().isBlank()
                    && institution != null && request.accountNumber() != null
                    && platform.ownsAccount(self, request.accountId().trim(), institution, request.accountNumber());
            if (!own) {
                throw new AccountLinkException(HttpStatus.FORBIDDEN, "You can only link an account you have saved yourself");
            }
        }
        AccountLinkEntity link = service.link(request.bankId(), request.accountNumber(), request.userId(),
                request.accountName(), caller == null ? null : String.valueOf(caller.getPrincipal()), request.accountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(link));
    }

    /** Retries delivery of anything still waiting on this account. */
    @PostMapping("/{id}/sync")
    public LinkView sync(@PathVariable Long id) {
        AccountLinkEntity link = service.get(id);
        service.deliverPending(link);
        return view(link);
    }

    /**
     * Removes the links to an account its owner is removing from the web app.
     * A customer may only remove links to their own account; an admin any.
     */
    @DeleteMapping("/by-account/{accountId}")
    public ResponseEntity<Map<String, Object>> unlinkAccount(@PathVariable String accountId, Authentication caller) {
        int removed = service.unlinkAccount(accountId, isCustomer(caller) ? String.valueOf(caller.getPrincipal()) : null);
        return ResponseEntity.ok(Map.of("removed", removed));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unlink(@PathVariable Long id) {
        service.unlink(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AccountLinkException.class)
    public ResponseEntity<Map<String, Object>> refused(AccountLinkException error) {
        return ResponseEntity.status(error.status())
                .body(Map.of("status", error.status().value(), "message", error.getMessage()));
    }

    private LinkView view(AccountLinkEntity link) {
        return new LinkView(link.getId(), link.getBankId(), link.getAccountNumber(), link.getUserId(),
                link.getAccountId(), link.getAccountName(), link.getCreatedAt(),
                service.pendingCount(link), service.lastError(link));
    }
}
