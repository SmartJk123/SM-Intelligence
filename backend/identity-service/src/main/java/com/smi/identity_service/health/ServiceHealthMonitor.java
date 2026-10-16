package com.smi.identity_service.health;

import com.smi.identity_service.service.PasswordResetMailer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Watches the platform's services and emails the administrators when one is
 * down or degraded, so a failure is noticed without anyone watching a screen.
 *
 * <ul>
 *   <li>A service is checked every interval (HEALTH_MONITOR_INTERVAL, 1 minute).</li>
 *   <li>It must fail FAILURES_BEFORE_ALERT checks in a row (2) before an alert,
 *       so a restart or a single slow answer does not page anyone.</li>
 *   <li>While it stays down a reminder goes out every HEALTH_ALERT_REMINDER (1 hour).</li>
 *   <li>When it is healthy again a "resolved" email says how long it was down.</li>
 * </ul>
 *
 * Recipients are HEALTH_ALERT_EMAILS, or ADMIN_EMAILS when that is not set.
 * Without SMTP the alert is still logged as a warning.
 */
@Component
public class ServiceHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ServiceHealthMonitor.class);
    private static final DateTimeFormatter EAST_AFRICA = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'EAT'")
            .withZone(ZoneId.of("Africa/Nairobi"));

    /** What is known about one service between checks. */
    static final class State {
        int consecutiveFailures;
        boolean alerting;
        Instant problemSince;
        Instant lastEmailAt;
        String lastProblem;
    }

    private final boolean enabled;
    private final Map<String, String> targets;
    private final List<String> recipients;
    private final int failuresBeforeAlert;
    private final Duration reminderInterval;
    private final HealthChecker checker;
    private final PasswordResetMailer mailer;
    private final Clock clock;
    private final Map<String, State> states = new LinkedHashMap<>();

    @Autowired
    public ServiceHealthMonitor(
            @Value("${app.health-monitor.enabled:true}") boolean enabled,
            @Value("${app.health-monitor.targets:}") String targets,
            @Value("${app.health-monitor.recipients:}") String recipients,
            @Value("${app.health-monitor.failures-before-alert:2}") int failuresBeforeAlert,
            @Value("${app.health-monitor.reminder-interval:PT1H}") Duration reminderInterval,
            HealthChecker checker,
            PasswordResetMailer mailer) {
        this(enabled, targets, recipients, failuresBeforeAlert, reminderInterval, checker, mailer, Clock.systemUTC());
    }

    ServiceHealthMonitor(boolean enabled, String targets, String recipients, int failuresBeforeAlert,
                         Duration reminderInterval, HealthChecker checker, PasswordResetMailer mailer, Clock clock) {
        this.enabled = enabled;
        this.targets = parseTargets(targets);
        this.recipients = Arrays.stream(recipients.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.failuresBeforeAlert = Math.max(1, failuresBeforeAlert);
        this.reminderInterval = reminderInterval;
        this.checker = checker;
        this.mailer = mailer;
        this.clock = clock;
        this.targets.keySet().forEach(name -> states.put(name, new State()));
        if (enabled && this.recipients.isEmpty()) {
            log.warn("Health monitor has no recipients: set HEALTH_ALERT_EMAILS or ADMIN_EMAILS to be emailed");
        }
    }

    /** name=url pairs, comma separated, e.g. accounts=http://localhost:8082. */
    static Map<String, String> parseTargets(String value) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : value.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = pair.substring(0, equals).trim();
            String url = pair.substring(equals + 1).trim().replaceAll("/+$", "");
            if (!name.isEmpty() && !url.isEmpty()) {
                parsed.put(name, url);
            }
        }
        return parsed;
    }

    @Scheduled(initialDelayString = "${app.health-monitor.initial-delay:PT2M}",
            fixedDelayString = "${app.health-monitor.interval:PT1M}")
    public void checkAll() {
        if (!enabled) {
            return;
        }
        targets.forEach((name, url) -> record(name, url, checker.check(url + "/actuator/health")));
    }

    void record(String name, String url, HealthChecker.Result result) {
        State state = states.get(name);
        Instant now = clock.instant();
        if (result.healthy()) {
            if (state.alerting) {
                Duration down = Duration.between(state.problemSince, now);
                log.info("Health monitor: {} is healthy again after {}", name, readable(down));
                email("RESOLVED: " + name + " is healthy again",
                        name + " is healthy again.\n\n"
                                + "It had a problem from " + EAST_AFRICA.format(state.problemSince)
                                + " until " + EAST_AFRICA.format(now) + " (" + readable(down) + ").\n"
                                + "Last problem seen: " + state.lastProblem + "\n");
            }
            states.put(name, new State());
            return;
        }
        if (state.consecutiveFailures == 0) {
            state.problemSince = now;
        }
        state.consecutiveFailures++;
        state.lastProblem = result.problem();
        if (!state.alerting && state.consecutiveFailures >= failuresBeforeAlert) {
            state.alerting = true;
            state.lastEmailAt = now;
            log.warn("Health monitor: {} has a problem: {}", name, result.problem());
            email("ALERT: " + name + " is " + headline(result.problem()), describe(name, url, state, now, false));
        } else if (state.alerting && !now.isBefore(state.lastEmailAt.plus(reminderInterval))) {
            state.lastEmailAt = now;
            log.warn("Health monitor: {} still has a problem: {}", name, result.problem());
            email("STILL DOWN: " + name + " is " + headline(result.problem()), describe(name, url, state, now, true));
        }
    }

    private String describe(String name, String url, State state, Instant now, boolean reminder) {
        return (reminder ? "This problem is still going on.\n\n" : "")
                + "Service:  " + name + "\n"
                + "Problem:  " + state.lastProblem + "\n"
                + "Since:    " + EAST_AFRICA.format(state.problemSince)
                + " (" + readable(Duration.between(state.problemSince, now)) + ")\n"
                + "Checked:  " + url + "/actuator/health\n\n"
                + "What to check: is the service running (.\\start-local.ps1 locally, the Render dashboard"
                + " in production)? For bank-integration a DEGRADED status means a bank or a cPanel import"
                + " is failing: see its log and the admin portal's bank pages.\n\n"
                + "You will get a reminder every " + readable(reminderInterval)
                + " while this lasts, and an email when it is resolved.\n";
    }

    private void email(String subject, String body) {
        for (String to : recipients) {
            mailer.send(to, "[SM-Intelligence] " + subject, body);
        }
    }

    private static String headline(String problem) {
        int bracket = problem.indexOf(" (");
        String first = bracket > 0 ? problem.substring(0, bracket) : problem;
        return first.startsWith("not reachable") ? "not reachable" : first;
    }

    private static String readable(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return hours + (hours == 1 ? " hour" : " hours") + (rest == 0 ? "" : " " + rest + " min");
    }
}
