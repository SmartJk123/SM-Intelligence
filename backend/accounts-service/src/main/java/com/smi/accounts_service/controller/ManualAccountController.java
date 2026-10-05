package com.smi.accounts_service.controller;

import com.smi.accounts_service.domain.Account;
import com.smi.accounts_service.dto.AccountResponse;
import com.smi.accounts_service.repository.AccountRepository;
import com.smi.accounts_service.exception.DuplicateAccountException;
import com.smi.accounts_service.service.AccountIdentity;
import com.smi.accounts_service.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/accounts/manual")
public class ManualAccountController {
    private final AccountIdentity identity;
    private final AccountRepository accounts;
    private final AccountService service;
    public ManualAccountController(AccountIdentity identity, AccountRepository accounts, AccountService service) {
        this.identity = identity; this.accounts = accounts; this.service = service;
    }
    public record Input(
        @NotBlank @Pattern(regexp="KCB|Equity|NCBA|Stanbic") String bank,
        @NotBlank @Size(max=150) String accountName,
        @NotBlank @Pattern(regexp="[0-9]{4,34}") String accountNumber,
        @NotBlank @Pattern(regexp="debit|credit") String cardType,
        @NotNull @DecimalMin("0") @Digits(integer=13, fraction=2) BigDecimal balance,
        @NotNull LocalDate balanceDate,
        @NotBlank @Pattern(regexp="KES") String currency
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AccountResponse create(@RequestHeader(value="Authorization", required=false) String authorization,
                                  @Valid @RequestBody Input input) {
        var owner = identity.owner(authorization);
        if (input.balanceDate().isAfter(LocalDate.now(ZoneId.of("Africa/Nairobi"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Balance date cannot be in the future");
        // A bank account number belongs to one person, so it is refused when any user already holds it.
        var holder = service.findHolder(input.bank(), input.accountNumber());
        if (holder.isPresent())
            throw new DuplicateAccountException(holder.get().getUserId().equals(owner)
                ? "You have already added this account"
                : "This account number is already registered to another user");
        // Persist a fingerprint instead of the full account number.
        var fingerprint = AccountService.fingerprint(input.bank(), input.accountNumber());
        boolean credit = input.cardType().equals("credit");
        var account = new Account(owner, fingerprint, input.accountName().trim(), input.bank(),
            credit ? "CREDIT" : "DEPOSIT", "•••• " + input.accountNumber().substring(input.accountNumber().length()-4),
            input.currency(), credit ? BigDecimal.ZERO : input.balance());
        account.setCreditOutstanding(credit ? input.balance() : BigDecimal.ZERO);
        account.setDataSource("MANUAL");
        account.setConnectionStatus("DISCONNECTED");
        account.setLastUpdated(input.balanceDate().atStartOfDay(ZoneId.of("Africa/Nairobi")).toOffsetDateTime());
        return AccountResponse.fromEntity(accounts.saveAndFlush(account));
    }
}
