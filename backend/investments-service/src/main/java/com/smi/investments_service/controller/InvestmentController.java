package com.smi.investments_service.controller;

import com.smi.investments_service.dto.InvestmentRequest;
import com.smi.investments_service.dto.InvestmentResponse;
import com.smi.investments_service.service.InvestmentService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's investments. Like budgets-service this trusts the ownerId it is
 * given: it is a private service, called only by the web adapter with the
 * signed-in user's id, and not routed through the public gateway.
 */
@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService service;

    public InvestmentController(InvestmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvestmentResponse> list(@RequestParam UUID ownerId) {
        return service.list(ownerId);
    }

    @PostMapping
    public ResponseEntity<InvestmentResponse> create(@RequestBody InvestmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public InvestmentResponse update(@PathVariable UUID id, @RequestBody InvestmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam UUID ownerId) {
        service.delete(id, ownerId);
        return ResponseEntity.noContent().build();
    }
}
