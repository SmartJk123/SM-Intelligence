package com.smi.identity_service.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.smi.identity_service.service.PasswordResetMailer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceHealthMonitorTest {

    /** A clock the test moves forward by hand. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-09T06:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    record Sent(String to, String subject, String body) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private final TestClock clock = new TestClock();
    private ServiceHealthMonitor monitor;

    private static final HealthChecker.Result UP = new HealthChecker.Result(true, null);
    private static final HealthChecker.Result DOWN = new HealthChecker.Result(false, "not reachable (ConnectException)");

    @BeforeEach
    void setUp() {
        PasswordResetMailer mailer = new PasswordResetMailer("", 587, "", "", "") {
            @Override
            public boolean send(String to, String subject, String text) {
                sent.add(new Sent(to, subject, text));
                return true;
            }
        };
        monitor = new ServiceHealthMonitor(true, "accounts=http://localhost:8082,bank-integration=http://localhost:8090",
                "ops@example.invalid, owner@example.invalid", 2, Duration.ofHours(1), new HealthChecker(), mailer, clock);
    }

    private void check(String name, HealthChecker.Result result) {
        monitor.record(name, "http://localhost:8082", result);
        clock.advance(Duration.ofMinutes(1));
    }

    @Test
    void aSingleFailedCheckSendsNothing() {
        check("accounts", DOWN);
        check("accounts", UP);

        assertThat(sent).isEmpty();
    }

    @Test
    void twoFailedChecksInARowAlertEveryRecipientOnce() {
        check("accounts", DOWN);
        check("accounts", DOWN);
        check("accounts", DOWN);

        assertThat(sent).hasSize(2);
        assertThat(sent).extracting(Sent::to).containsExactly("ops@example.invalid", "owner@example.invalid");
        assertThat(sent.get(0).subject()).isEqualTo("[SM-Intelligence] ALERT: accounts is not reachable");
        assertThat(sent.get(0).body()).contains("Since:    9 Oct 2026, 09:00 EAT");
    }

    @Test
    void remindsHourlyWhileDownAndSaysWhenResolved() {
        check("accounts", DOWN);
        check("accounts", DOWN);
        sent.clear();

        // The alert went out at 06:01; the hourly reminder is due at 07:01.
        clock.advance(Duration.ofMinutes(58));
        check("accounts", DOWN);
        assertThat(sent).isEmpty();
        check("accounts", DOWN);
        assertThat(sent).extracting(Sent::subject).containsOnly("[SM-Intelligence] STILL DOWN: accounts is not reachable");
        sent.clear();

        check("accounts", UP);
        assertThat(sent).extracting(Sent::subject).containsOnly("[SM-Intelligence] RESOLVED: accounts is healthy again");
        assertThat(sent.get(0).body()).contains("from 9 Oct 2026, 09:00 EAT until 9 Oct 2026, 10:02 EAT (1 hour 2 min)");
        sent.clear();

        check("accounts", UP);
        assertThat(sent).isEmpty();
    }

    @Test
    void aDegradedBankIsAnAlertToo() {
        HealthChecker.Result degraded = new HealthChecker.Result(false, "DEGRADED (HTTP 200)");
        check("bank-integration", degraded);
        check("bank-integration", degraded);

        assertThat(sent.get(0).subject()).isEqualTo("[SM-Intelligence] ALERT: bank-integration is DEGRADED");
    }

    @Test
    void servicesAreTrackedSeparately() {
        check("accounts", DOWN);
        check("bank-integration", DOWN);
        check("accounts", UP);
        check("bank-integration", UP);

        assertThat(sent).isEmpty();
    }

    @Test
    void readsNameAddressPairs() {
        assertThat(ServiceHealthMonitor.parseTargets(" accounts = http://a:1/ ,bad, =x, bank=http://b:2"))
                .containsExactly(java.util.Map.entry("accounts", "http://a:1"), java.util.Map.entry("bank", "http://b:2"));
    }
}
