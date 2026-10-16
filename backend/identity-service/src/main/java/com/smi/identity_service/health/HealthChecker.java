package com.smi.identity_service.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Asks one service for its /actuator/health. Only UP counts as healthy:
 * bank-integration-service answers DEGRADED with HTTP 200 when a bank is
 * failing, and that is worth an alert too.
 */
@Component
public class HealthChecker {

    /** The overall status, which is the only one an anonymous caller sees. */
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** @param healthy true only for UP; problem says what is wrong otherwise. */
    public record Result(boolean healthy, String problem) {
        static Result up() {
            return new Result(true, null);
        }

        static Result problem(String problem) {
            return new Result(false, problem);
        }
    }

    public Result check(String healthUrl) {
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(healthUrl)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Matcher status = STATUS.matcher(response.body());
            String reported = status.find() ? status.group(1) : "UNKNOWN";
            if (response.statusCode() == 200 && "UP".equals(reported)) {
                return Result.up();
            }
            return Result.problem(reported + " (HTTP " + response.statusCode() + ")");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.problem("check interrupted");
        } catch (Exception error) {
            return Result.problem("not reachable (" + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : ": " + error.getMessage()) + ")");
        }
    }
}
