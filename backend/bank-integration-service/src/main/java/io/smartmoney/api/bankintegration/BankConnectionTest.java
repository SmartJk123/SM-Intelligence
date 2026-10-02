package io.smartmoney.api.bankintegration;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of a connection test. The shape matches ConnectionTestResult in the
 * Angular application.
 */
public record BankConnectionTest(
        boolean ok,
        String summary,
        long latencyMs,
        List<ConnectionStep> steps,
        Instant testedAt) {

    public static BankConnectionTest of(List<ConnectionStep> steps, long latencyMs) {
        long failed = steps.stream().filter(step -> step.status() == StepStatus.FAIL).count();
        long warned = steps.stream().filter(step -> step.status() == StepStatus.WARN).count();

        String summary;
        if (failed > 0) {
            String firstFailure = steps.stream()
                    .filter(step -> step.status() == StepStatus.FAIL)
                    .map(ConnectionStep::name)
                    .findFirst()
                    .orElse("an unknown step");
            summary = "Connection test failed at " + firstFailure + ".";
        } else if (warned > 0) {
            summary = "Connection test passed with " + warned + (warned == 1 ? " warning." : " warnings.");
        } else {
            summary = "Connection test passed. Authentication and the gateway are reachable.";
        }

        return new BankConnectionTest(failed == 0, summary, latencyMs, steps, Instant.now());
    }
}
