package io.smartmoney.api.accountlink;

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

    public AccountLinkController(AccountLinkService service) {
        this.service = service;
    }

    @GetMapping
    public List<LinkView> list(@RequestParam(required = false) String userId) {
        return service.list(userId).stream().map(this::view).toList();
    }

    @PostMapping
    public ResponseEntity<LinkView> link(@RequestBody LinkRequest request, Authentication admin) {
        AccountLinkEntity link = service.link(request.bankId(), request.accountNumber(), request.userId(),
                request.accountName(), admin == null ? null : String.valueOf(admin.getPrincipal()), request.accountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(link));
    }

    /** Retries delivery of anything still waiting on this account. */
    @PostMapping("/{id}/sync")
    public LinkView sync(@PathVariable Long id) {
        AccountLinkEntity link = service.get(id);
        service.deliverPending(link);
        return view(link);
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
