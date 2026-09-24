package com.smi.transactions_service.invoice;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceControllerTest {
    InvoiceRepository repository;
    InvoiceIdentity identity;
    MockMvc mvc;
    UUID owner = UUID.randomUUID();
    @BeforeEach void setup() {
        repository = mock(InvoiceRepository.class); identity = mock(InvoiceIdentity.class);
        when(identity.owner("Bearer test")).thenReturn(owner);
        mvc = MockMvcBuilders.standaloneSetup(new InvoiceController(repository, identity)).build();
    }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(byte[] bytes) {
        return multipart("/api/invoices").file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", bytes))
            .header("Authorization", "Bearer test").param("vendor", "Test Supplier").param("amount", "1250.50")
            .param("currency", "KES").param("invoiceDate", "2026-09-24");
    }
    @Test void createsPendingRecordWithVerifiedOwnerAndDocument() throws Exception {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(upload("%PDF-test invoice".getBytes())).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.amount").value(1250.50))
            .andExpect(jsonPath("$.source").value("INVOICE")).andExpect(jsonPath("$.document").doesNotExist());
        var saved = org.mockito.ArgumentCaptor.forClass(Invoice.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals(owner, saved.getValue().ownerId);
        assertArrayEquals("%PDF-test invoice".getBytes(), saved.getValue().document);
    }
    @Test void rejectsDuplicateAndInvalidUploads() throws Exception {
        mvc.perform(upload("executable disguised as pdf".getBytes())).andExpect(status().isBadRequest());
        mvc.perform(upload(new byte[10 * 1024 * 1024 + 1])).andExpect(status().isBadRequest());
        when(repository.findByOwnerIdAndDocumentHash(eq(owner), anyString())).thenReturn(Optional.of(new Invoice()));
        mvc.perform(upload("%PDF-duplicate".getBytes())).andExpect(status().isConflict());
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void scopesListingAndDownloadToVerifiedUser() throws Exception {
        when(repository.findByOwnerIdOrderByCreatedAtDesc(eq(owner), any())).thenReturn(List.of());
        mvc.perform(get("/api/invoices").header("Authorization", "Bearer test")).andExpect(status().isOk()).andExpect(content().json("[]"));
        UUID otherInvoice = UUID.randomUUID();
        mvc.perform(get("/api/invoices/" + otherInvoice + "/document").header("Authorization", "Bearer test")).andExpect(status().isNotFound());
        verify(repository).findByIdAndOwnerId(otherInvoice, owner);
    }
    @Test void deletesOnlyInvoicesOwnedByVerifiedUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.deleteOwned(id, owner)).thenReturn(1);
        mvc.perform(delete("/api/invoices/" + id).header("Authorization", "Bearer test"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(repository).deleteOwned(id, owner);
        UUID inaccessible = UUID.randomUUID();
        mvc.perform(delete("/api/invoices/" + inaccessible).header("Authorization", "Bearer test"))
            .andExpect(status().isNotFound());
        verify(repository).deleteOwned(inaccessible, owner);
    }
    @Test void refusesUnauthenticatedAccess() throws Exception {
        when(identity.owner(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/invoices/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
}
