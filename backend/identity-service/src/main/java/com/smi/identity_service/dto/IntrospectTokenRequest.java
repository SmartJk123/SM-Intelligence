package com.smi.identity_service.dto;

/**
 * Optional payload for token introspection: caller can provide token in body or in Authorization header.
 */
public record IntrospectTokenRequest(String token) {
}
