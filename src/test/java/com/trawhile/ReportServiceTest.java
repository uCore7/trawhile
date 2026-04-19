package com.trawhile;

import com.trawhile.domain.AuthLevel;
import com.trawhile.domain.TimeRecord;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.service.ReportService;
import com.trawhile.web.dto.Report;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TE-F036.F01-04
 */
class ReportServiceTest {

    @Test
    @Tag("TE-F036.F01-04")
    void getReport_timestampsConvertedToCompanyTimezone() throws Exception {
        UUID sharedId = UUID.randomUUID();
        OffsetDateTime utcStartedAt = OffsetDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        OffsetDateTime utcEndedAt = utcStartedAt.plusHours(2);
        TimeRecord record = new TimeRecord(
            UUID.randomUUID(),
            sharedId,
            sharedId,
            utcStartedAt,
            utcEndedAt,
            "UTC",
            "Summer entry",
            utcStartedAt.minusDays(1)
        );

        TimeRecordRepository timeRecordRepository = stubTimeRecordRepository(record);
        AuthorizationQueries authorizationQueries = new StubAuthorizationQueries(sharedId);

        ReportService service = new ReportService(timeRecordRepository, authorizationQueries);
        Method reportMethod = Arrays.stream(ReportService.class.getDeclaredMethods())
            .filter(method -> method.getReturnType().equals(Report.class))
            .filter(method -> method.getName().contains("Report"))
            .findFirst()
            .orElse(null);

        assertThat(reportMethod)
            .as("ReportService should expose a report-building method that returns Report")
            .isNotNull();

        reportMethod.setAccessible(true);
        Report report = (Report) reportMethod.invoke(
            service,
            buildArguments(reportMethod.getParameterTypes(), sharedId)
        );

        OffsetDateTime expectedStartedAt = utcStartedAt.atZoneSameInstant(ZoneId.of("Europe/Berlin"))
            .toOffsetDateTime();
        OffsetDateTime expectedEndedAt = utcEndedAt.atZoneSameInstant(ZoneId.of("Europe/Berlin"))
            .toOffsetDateTime();

        assertThat(report.getMode()).isEqualTo(Report.ModeEnum.DETAILED);
        assertThat(report.getDetailed()).hasSize(1);
        assertThat(report.getDetailed().get(0).getStartedAt()).isEqualTo(expectedStartedAt);
        assertThat(report.getDetailed().get(0).getEndedAt()).isEqualTo(expectedEndedAt);
    }

    private Object[] buildArguments(Class<?>[] parameterTypes, UUID sharedId) {
        Object[] arguments = new Object[parameterTypes.length];
        int localDateIndex = 0;

        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];
            if (parameterType.equals(String.class)) {
                arguments[index] = "detailed";
                continue;
            }
            if (parameterType.equals(Report.ModeEnum.class)) {
                arguments[index] = Report.ModeEnum.DETAILED;
                continue;
            }
            if (parameterType.equals(LocalDate.class)) {
                arguments[index] = LocalDate.of(2024, 6, 15 + localDateIndex);
                localDateIndex++;
                continue;
            }
            if (parameterType.equals(UUID.class)) {
                arguments[index] = sharedId;
                continue;
            }
            if (parameterType.equals(Boolean.class) || parameterType.equals(boolean.class)) {
                arguments[index] = Boolean.FALSE;
                continue;
            }
            arguments[index] = null;
        }

        return arguments;
    }

    @SuppressWarnings("unchecked")
    private TimeRecordRepository stubTimeRecordRepository(TimeRecord record) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "findAll", "findByUserIdOrderByStartedAtDesc" -> List.of(record);
            case "findByUserIdAndEndedAtIsNull" -> Optional.empty();
            case "toString" -> "StubTimeRecordRepository";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        };
        return (TimeRecordRepository) Proxy.newProxyInstance(
            TimeRecordRepository.class.getClassLoader(),
            new Class<?>[] {TimeRecordRepository.class},
            handler
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(long.class) || returnType.equals(int.class) || returnType.equals(short.class)) {
            return 0;
        }
        return null;
    }

    private static final class StubAuthorizationQueries extends AuthorizationQueries {

        private final UUID visibleNodeId;

        private StubAuthorizationQueries(UUID visibleNodeId) {
            super(new NamedParameterJdbcTemplate(new DriverManagerDataSource()));
            this.visibleNodeId = visibleNodeId;
        }

        @Override
        public List<UUID> visibleNodeIds(UUID userId) {
            return List.of(visibleNodeId);
        }

        @Override
        public boolean hasAuthorization(UUID userId, UUID nodeId, AuthLevel required) {
            return true;
        }
    }
}
