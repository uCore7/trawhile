package com.trawhile.repository.read;

import com.trawhile.BaseIT;
import com.trawhile.TestFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportReadQueriesIT extends BaseIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Tag("persistence-sensitive-reads")
    void findOwnDetailedRecordsReturnsOnlyActingUsersRowsWithinDateRangeAndSubtree() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Owner");
        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Other");
        UUID visibleParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Parent");
        UUID visibleChildId = TestFixtures.insertNode(jdbc, visibleParentId, "Visible Child");
        UUID otherBranchId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Other Branch");
        OffsetDateTime base = OffsetDateTime.of(2024, 6, 20, 8, 0, 0, 0, ZoneOffset.UTC);

        UUID matchingRecordId = insertTimeRecord(
            actingUserId,
            visibleChildId,
            base,
            base.plusHours(2),
            "UTC",
            "Matching own record"
        );
        UUID outOfSubtreeRecordId = insertTimeRecord(
            actingUserId,
            otherBranchId,
            base.plusHours(1),
            base.plusHours(2),
            "UTC",
            "Wrong subtree"
        );
        UUID outOfRangeRecordId = insertTimeRecord(
            actingUserId,
            visibleChildId,
            base.minusDays(3),
            base.minusDays(3).plusHours(1),
            "UTC",
            "Wrong date"
        );
        UUID otherUsersRecordId = insertTimeRecord(
            otherUserId,
            visibleChildId,
            base.plusHours(3),
            base.plusHours(4),
            "UTC",
            "Other user"
        );

        List<?> detailedRows = findOwnDetailedRecords(
            actingUserId,
            visibleParentId,
            LocalDate.of(2024, 6, 20),
            LocalDate.of(2024, 6, 20)
        );

        assertThat(detailedRows)
            .extracting(this::recordId)
            .containsExactly(matchingRecordId)
            .doesNotContain(outOfSubtreeRecordId, outOfRangeRecordId, otherUsersRecordId);
        assertThat(detailedRows)
            .extracting(row -> readProperty(row, "userId", "ownerId"))
            .containsOnly(actingUserId);
    }

    @Test
    @Tag("persistence-sensitive-reads")
    void findVisibleMemberSummariesReturnsAggregateOnlyRowsForAuthorizedSubtree() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID visibleMemberId = TestFixtures.insertUserWithProfile(jdbc, "Visible Member");
        UUID hiddenMemberId = TestFixtures.insertUserWithProfile(jdbc, "Hidden Member");
        UUID visibleParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Parent");
        UUID visibleChildId = TestFixtures.insertNode(jdbc, visibleParentId, "Visible Child");
        UUID hiddenBranchId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Branch");
        TestFixtures.grantAuth(jdbc, actingUserId, visibleParentId, "view");
        OffsetDateTime base = OffsetDateTime.of(2024, 6, 21, 9, 0, 0, 0, ZoneOffset.UTC);

        insertTimeRecord(visibleMemberId, visibleChildId, base, base.plusHours(2), "Europe/Zurich", "visible 1");
        insertTimeRecord(visibleMemberId, visibleChildId, base.plusHours(3), base.plusHours(4), "UTC", "visible 2");
        insertTimeRecord(hiddenMemberId, hiddenBranchId, base, base.plusHours(5), "UTC", "hidden branch");
        insertTimeRecord(visibleMemberId, visibleChildId, base.minusDays(2), base.minusDays(2).plusHours(7), "UTC", "old");

        List<?> summaries = findVisibleMemberSummaries(
            actingUserId,
            visibleParentId,
            LocalDate.of(2024, 6, 21),
            LocalDate.of(2024, 6, 21),
            "day",
            null
        );

        assertThat(extractUserIds(summaries))
            .contains(visibleMemberId)
            .doesNotContain(hiddenMemberId);
        assertThat(containsTotalSeconds(summaries, visibleMemberId, 10_800L)).isTrue();
        assertThat(summaries).allSatisfy(this::assertNoRawTimeRecordDetailFields);
    }

    private List<?> findOwnDetailedRecords(UUID actingUserId, UUID nodeId, LocalDate from, LocalDate to) {
        Object result = invokeReportReadQueries(
            "findOwnDetailedRecords",
            new Object[] {actingUserId, nodeId, from, to},
            new Object[] {actingUserId, from, to, nodeId}
        );
        return asItemList(result);
    }

    private List<?> findVisibleMemberSummaries(
        UUID actingUserId,
        UUID nodeId,
        LocalDate from,
        LocalDate to,
        String interval,
        Boolean hasDataQualityIssues
    ) {
        Object result = invokeReportReadQueries(
            "findVisibleMemberSummaries",
            new Object[] {actingUserId, nodeId, from, to, interval, hasDataQualityIssues},
            new Object[] {actingUserId, from, to, nodeId, interval, hasDataQualityIssues},
            new Object[] {actingUserId, nodeId, from, to, interval},
            new Object[] {actingUserId, from, to, nodeId, interval}
        );
        return asItemList(result);
    }

    private List<UUID> extractUserIds(List<?> rows) {
        List<UUID> userIds = new ArrayList<>();
        for (Object row : rows) {
            readOptionalProperty(row, "userId", "memberId").ifPresent(value -> userIds.add((UUID) value));
        }
        return userIds;
    }

    private boolean containsTotalSeconds(List<?> rows, UUID userId, long expectedTotalSeconds) {
        return rows.stream()
            .filter(row -> userId.equals(readOptionalProperty(row, "userId", "memberId").orElse(null)))
            .anyMatch(row -> objectContainsTotalSeconds(row, expectedTotalSeconds));
    }

    private boolean objectContainsTotalSeconds(Object target, long expectedTotalSeconds) {
        Optional<Object> directTotal = readOptionalProperty(target, "totalSeconds", "durationSeconds", "totalDurationSeconds");
        if (directTotal.isPresent() && directTotal.orElseThrow() instanceof Number number) {
            return number.longValue() == expectedTotalSeconds;
        }

        Optional<Object> buckets = readOptionalProperty(target, "buckets", "rows", "items");
        if (buckets.isPresent() && buckets.orElseThrow() instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> objectContainsTotalSeconds(item, expectedTotalSeconds));
        }

        return false;
    }

    private void assertNoRawTimeRecordDetailFields(Object row) {
        assertThat(readOptionalProperty(row, "startedAt", "started_at")).isEmpty();
        assertThat(readOptionalProperty(row, "endedAt", "ended_at")).isEmpty();
        assertThat(readOptionalProperty(row, "timezone")).isEmpty();
        assertThat(readOptionalProperty(row, "description")).isEmpty();

        Optional<Object> buckets = readOptionalProperty(row, "buckets");
        if (buckets.isPresent() && buckets.orElseThrow() instanceof Collection<?> collection) {
            collection.forEach(bucket -> {
                assertThat(readOptionalProperty(bucket, "startedAt", "started_at")).isEmpty();
                assertThat(readOptionalProperty(bucket, "endedAt", "ended_at")).isEmpty();
                assertThat(readOptionalProperty(bucket, "timezone")).isEmpty();
                assertThat(readOptionalProperty(bucket, "description")).isEmpty();
            });
        }
    }

    private UUID recordId(Object row) {
        return (UUID) readProperty(row, "id", "recordId");
    }

    private UUID insertTimeRecord(
        UUID userId,
        UUID nodeId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String timezone,
        String description
    ) {
        UUID recordId = UUID.randomUUID();
        jdbc.update(
            """
                INSERT INTO time_records (id, user_id, node_id, started_at, ended_at, timezone, description)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            recordId,
            userId,
            nodeId,
            startedAt,
            endedAt,
            timezone,
            description
        );
        return recordId;
    }

    private Object invokeReportReadQueries(String methodName, Object[]... argumentVariants) {
        Object bean = readQueriesBean();
        List<String> availableSignatures = new ArrayList<>();
        for (Method method : bean.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            availableSignatures.add(method.toGenericString());
            for (Object[] arguments : argumentVariants) {
                Optional<Object[]> converted = convertArguments(method.getParameterTypes(), arguments);
                if (converted.isEmpty()) {
                    continue;
                }
                try {
                    return method.invoke(bean, converted.orElseThrow());
                } catch (IllegalAccessException ex) {
                    throw new AssertionError("Could not access ReportReadQueries method " + methodName, ex);
                } catch (InvocationTargetException ex) {
                    throw new AssertionError(
                        "ReportReadQueries method " + methodName + " threw an exception",
                        ex.getTargetException()
                    );
                }
            }
        }
        throw new AssertionError(
            "Expected ReportReadQueries to expose compatible method " + methodName
                + "; available signatures: " + availableSignatures
        );
    }

    private Object readQueriesBean() {
        try {
            Class<?> readQueriesType = Class.forName("com.trawhile.repository.read.ReportReadQueries");
            return applicationContext.getBean(readQueriesType);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected com.trawhile.repository.read.ReportReadQueries to exist", ex);
        }
    }

    private Optional<Object[]> convertArguments(Class<?>[] parameterTypes, Object[] arguments) {
        if (parameterTypes.length != arguments.length) {
            return Optional.empty();
        }

        Object[] converted = new Object[arguments.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            Optional<Object> convertedArgument = convertArgument(parameterTypes[index], arguments[index]);
            if (convertedArgument.isEmpty()) {
                return Optional.empty();
            }
            converted[index] = convertedArgument.orElse(null);
        }
        return Optional.of(converted);
    }

    private Optional<Object> convertArgument(Class<?> parameterType, Object argument) {
        if (argument == null) {
            if (parameterType.isPrimitive()) {
                return Optional.empty();
            }
            if (Optional.class.equals(parameterType)) {
                return Optional.of(Optional.empty());
            }
            return Optional.ofNullable(null);
        }

        if (Optional.class.equals(parameterType)) {
            return Optional.of(argument instanceof Optional<?> optional ? optional : Optional.of(argument));
        }

        Class<?> wrappedType = wrap(parameterType);
        if (wrappedType.isInstance(argument)) {
            return Optional.of(argument);
        }

        if (parameterType.isEnum() && argument instanceof String value) {
            for (Object constant : parameterType.getEnumConstants()) {
                Enum<?> enumConstant = (Enum<?>) constant;
                if (enumConstant.name().equalsIgnoreCase(value)
                    || enumConstant.name().replace('_', '-').equalsIgnoreCase(value)
                    || enumConstant.name().replace('_', ' ').equalsIgnoreCase(value)) {
                    return Optional.of(enumConstant);
                }
            }
        }

        if (Number.class.isAssignableFrom(wrappedType) && argument instanceof Number number) {
            if (wrappedType.equals(Integer.class)) {
                return Optional.of(number.intValue());
            }
            if (wrappedType.equals(Long.class)) {
                return Optional.of(number.longValue());
            }
        }

        return Optional.empty();
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            default -> type;
        };
    }

    private List<?> asItemList(Object result) {
        if (result instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }

        Optional<Object> items = readOptionalProperty(
            result,
            "items",
            "rows",
            "results",
            "records",
            "detailed",
            "summary"
        );
        if (items.isPresent() && items.orElseThrow() instanceof Collection<?> collection) {
            return List.copyOf(collection);
        }

        throw new AssertionError("Expected a collection-like result but got " + result.getClass().getName());
    }

    private Object readProperty(Object target, String... candidateNames) {
        return readOptionalProperty(target, candidateNames)
            .orElseThrow(() -> new AssertionError(
                "Could not read any of properties " + List.of(candidateNames) + " from " + target.getClass().getName()
            ));
    }

    private Optional<Object> readOptionalProperty(Object target, String... candidateNames) {
        if (target instanceof Map<?, ?> map) {
            for (String candidateName : candidateNames) {
                if (map.containsKey(candidateName)) {
                    return Optional.ofNullable(map.get(candidateName));
                }
            }
        }

        for (String candidateName : candidateNames) {
            Method accessor = findAccessor(target.getClass(), candidateName);
            if (accessor == null) {
                continue;
            }
            try {
                return Optional.ofNullable(accessor.invoke(target));
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Could not read property " + candidateName, ex);
            } catch (InvocationTargetException ex) {
                throw new AssertionError("Accessor for property " + candidateName + " threw an exception", ex);
            }
        }

        return Optional.empty();
    }

    private Method findAccessor(Class<?> type, String propertyName) {
        for (String methodName : List.of(
            propertyName,
            "get" + capitalize(propertyName),
            "is" + capitalize(propertyName)
        )) {
            try {
                return type.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                // Try the next conventional accessor name.
            }
        }
        return null;
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
