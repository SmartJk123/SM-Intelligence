package io.smartmoney.api.bankintegration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the most recent connection test per bank, for reporting on screen.
 *
 * The result is written to the database as well as held in memory. Without that
 * every restart showed all four banks as never tested, and the integrations
 * page then reported zero connected banks until each test was run again by hand.
 */
@Component
public class ConnectionTestRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTestRegistry.class);
    private static final int MAX_STEPS = 4000;

    private final Map<String, BankConnectionTest> latest = new ConcurrentHashMap<>();
    private final BankConnectionTestRepository repository;
    private final ObjectMapper mapper;

    public ConnectionTestRegistry(BankConnectionTestRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @PostConstruct
    void loadStoredTests() {
        try {
            for (BankConnectionTestEntity entity : repository.findAll()) {
                read(entity).ifPresent(test -> latest.put(entity.getBankId(), test));
            }
        } catch (RuntimeException error) {
            // A database that cannot be read leaves the column empty rather than
            // stopping the service. The next test fills it again.
            log.warn("Could not read the stored connection tests: {}", error.getMessage());
        }
    }

    public void record(String bankId, BankConnectionTest test) {
        latest.put(bankId, test);
        try {
            repository.save(new BankConnectionTestEntity(bankId, test, writeSteps(test.steps())));
        } catch (RuntimeException error) {
            // A result that cannot be stored is still worth showing now.
            log.warn("Could not store the {} connection test: {}", bankId, error.getMessage());
        }
    }

    public Optional<BankConnectionTest> latestFor(String bankId) {
        return Optional.ofNullable(latest.get(bankId));
    }

    private String writeSteps(List<ConnectionStep> steps) {
        try {
            String json = mapper.writeValueAsString(steps);
            return json.length() <= MAX_STEPS ? json : json.substring(0, MAX_STEPS);
        } catch (Exception error) {
            return null;
        }
    }

    private Optional<BankConnectionTest> read(BankConnectionTestEntity entity) {
        try {
            List<ConnectionStep> steps = entity.getSteps() == null || entity.getSteps().isBlank()
                    ? List.of()
                    : mapper.readValue(entity.getSteps(), new TypeReference<List<ConnectionStep>>() { });
            return Optional.of(new BankConnectionTest(
                    entity.isPassed(),
                    entity.getSummary(),
                    entity.getLatencyMs(),
                    steps,
                    entity.getTestedAt()));
        } catch (Exception error) {
            log.warn("The stored {} connection test could not be read: {}", entity.getBankId(), error.getMessage());
            return Optional.empty();
        }
    }
}
