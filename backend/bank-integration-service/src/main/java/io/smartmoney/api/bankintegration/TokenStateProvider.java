package io.smartmoney.api.bankintegration;

/** Implemented by connectors that hold an OAuth access token. */
public interface TokenStateProvider {

    TokenSnapshot tokenSnapshot();
}
