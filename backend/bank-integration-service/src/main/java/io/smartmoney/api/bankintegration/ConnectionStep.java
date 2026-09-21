package io.smartmoney.api.bankintegration;

public record ConnectionStep(StepKey key, String name, StepStatus status, String detail) {

    public static ConnectionStep ok(StepKey key, String name, String detail) {
        return new ConnectionStep(key, name, StepStatus.OK, detail);
    }

    public static ConnectionStep warn(StepKey key, String name, String detail) {
        return new ConnectionStep(key, name, StepStatus.WARN, detail);
    }

    public static ConnectionStep fail(StepKey key, String name, String detail) {
        return new ConnectionStep(key, name, StepStatus.FAIL, detail);
    }
}
