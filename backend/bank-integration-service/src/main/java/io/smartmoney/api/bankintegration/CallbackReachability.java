package io.smartmoney.api.bankintegration;

import io.smartmoney.api.config.PlatformProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Answers whether the configured callback address can actually receive a
 * notification from outside this machine.
 *
 * {@link PlatformProperties#callbackIsPublic()} compares strings, so it reports
 * an HTTPS address that is not localhost as public. A quick tunnel is issued a
 * new hostname every time it starts, which means the previous address stays
 * eligible for that check long after it stopped resolving. The connection test
 * therefore reported a green webhook step for an address no bank could deliver
 * to, and the admin interface showed a healthy webhook tile with it. The
 * hostname is resolved as well so that cannot happen.
 */
@Component
public class CallbackReachability {

    /** How the callback address looks from outside this machine. */
    public enum State {
        /** A public HTTPS address whose host resolves. */
        RESOLVABLE,
        /** An address only this machine can reach, such as localhost. */
        LOCAL,
        /** A public looking address whose host no longer resolves. */
        UNRESOLVABLE
    }

    /** Long enough for a slow resolver, short enough not to stall a test. */
    private static final long RESOLVE_TIMEOUT_SECONDS = 3;

    private final PlatformProperties platform;

    public CallbackReachability(PlatformProperties platform) {
        this.platform = platform;
    }

    public State state(String bankId) {
        if (!platform.callbackIsPublic()) {
            return State.LOCAL;
        }
        return resolves(platform.callbackUrl(bankId)) ? State.RESOLVABLE : State.UNRESOLVABLE;
    }

    /**
     * Resolution runs on its own thread so that a resolver which never answers
     * cannot hold up the connection test.
     */
    private static boolean resolves(String callbackUrl) {
        String host;
        try {
            host = URI.create(callbackUrl).getHost();
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (host == null || host.isBlank()) {
            return false;
        }

        CompletableFuture<Boolean> resolved = new CompletableFuture<>();
        Thread probe = new Thread(() -> {
            try {
                InetAddress.getByName(host);
                resolved.complete(true);
            } catch (Exception error) {
                resolved.complete(false);
            }
        }, "callback-reachability");
        probe.setDaemon(true);
        probe.start();

        try {
            return resolved.get(RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception error) {
            return false;
        }
    }
}