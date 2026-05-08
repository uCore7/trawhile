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

class McpReadQueriesIT extends BaseIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Tag("persistence-sensitive-reads")
    void findVisibleNodeTreeReturnsOnlyAuthorizedNodes() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");
        UUID visibleParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Parent");
        UUID visibleChildId = TestFixtures.insertNode(jdbc, visibleParentId, "Visible Child");
        UUID hiddenParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Parent");
        UUID hiddenChildId = TestFixtures.insertNode(jdbc, hiddenParentId, "Hidden Child");
        TestFixtures.grantAuth(jdbc, actingUserId, visibleParentId, "view");

        List<?> visibleNodes = flattenTree(findVisibleNodeTree(actingUserId));

        assertThat(visibleNodes)
            .extracting(node -> readProperty(node, "id", "nodeId"))
            .contains(visibleParentId, visibleChildId)
            .doesNotContain(hiddenParentId, hiddenChildId);
        assertThat(visibleNodes)
            .extracting(node -> readOptionalProperty(node, "name", "nodeName").orElse(null))
            .contains("Visible Parent", "Visible Child")
            .doesNotContain("Hidden Parent", "Hidden Child");
    }

    @Test
    @Tag("persistence-sensitive-reads")
    void findOwnDetailedRecordsReturnsOnlyTokenOwnersRows() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Token Owner");
        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Other User");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Shared Node");
        TestFixtures.grantAuth(jdbc, actingUserId, nodeId, "track");
        OffsetDateTime startedAt = OffsetDateTime.of(2024, 6, 22, 8, 0, 0, 0, ZoneOffset.UTC);

        UUID ownRecordId = insertTimeRecord(
            actingUserId,
            nodeId,
            startedAt,
            startedAt.plusHours(2),
            "Europe/Zurich",
            "Own detailed row"
        );
        UUID otherRecordId = insertTimeRecord(
            otherUserId,
            nodeId,
            startedAt.plusHours(3),
            startedAt.plusHours(4),
            "UTC",
            "Other detailed row"
        );

        List<?> detailedRows = findOwnDetailedRecords(
            actingUserId,
            nodeId,
            LocalDate.of(2024, 6, 22),
            LocalDate.of(2024, 6, 22)
        );

        assertThat(detailedRows)
            .extracting(this::recordId)
            .containsExactly(ownRecordId)
            .doesNotContain(otherRecordId);
        assertThat(detailedRows)
            .extracting(row -> readProperty(row, "userId", "ownerId"))
            .containsOnly(actingUserId);
    }

    @Test
    @Tag("persistence-sensitive-reads")
    void findVisibleDailyTotalsForOtherUserReturnsAggregateOnlyRows() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID targetUserId = TestFixtures.insertUserWithProfile(jdbc, "Member");
        UUID visibleNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Node");
        UUID hiddenNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Node");
        TestFixtures.grantAuth(jdbc, actingUserId, visibleNodeId, "view");
        OffsetDateTime base = OffsetDateTime.of(2024, 6, 23, 8, 0, 0, 0, ZoneOffset.UTC);

        UUID firstVisibleRecordId = insertTimeRecord(
            targetUserId,
            visibleNodeId,
            base,
            base.plusHours(2),
            "UTC",
            "Morning session"
        );
        UUID secondVisibleRecordId = insertTimeRecord(
            targetUserId,
            visibleNodeId,
            base.plusHours(3),
            base.plusHours(4),
            "UTC",
            "Afternoon session"
        );
        insertTimeRecord(
            targetUserId,
            hiddenNodeId,
            base.plusHours(1),
            base.plusHours(6),
            "UTC",
            "Hidden session"
        );

        List<?> dailyTotals = findVisibleDailyTotalsForOtherUser(
            actingUserId,
            targetUserId,
            visibleNodeId,
            LocalDate.of(2024, 6, 23),
            LocalDate.of(2024, 6, 23)
        );

        assertThat(dailyTotals).isNotEmpty();
        assertThat(dailyTotals)
            .extracting(row -> readOptionalProperty(row, "userId", "memberId").orElse(null))
            .contains(targetUserId)
            .doesNotContainNull();
        assertThat(dailyTotals).anySatisfy(row -> {
            assertThat(objectContainsTotalSeconds(row, 10_800L)).isTrue();
            assertThat(readOptionalProperty(row, "startedAt", "started_at")).isEmpty();
            assertThat(readOptionalProperty(row, "endedAt", "ended_at")).isEmpty();
            assertThat(readOptionalProperty(row, "timezone")).isEmpty();
            assertThat(readOptionalProperty(row, "description")).isEmpty();
            assertThat(row.toString())
                .doesNotContain(firstVisibleRecordId.toString())
                .doesNotContain(secondVisibleRecordId.toString());
        });
    }

    private Object findVisibleNodeTree(UUID actingUserId) {
        return invokeMcpReadQueries("findVisibleNodeTree", new Object[] {actingUserId});
    }

    private List<?> findOwnDetailedRecords(UUID actingUserId, UUID nodeId, LocalDate from, LocalDate to) {
        Object result = invokeMcpReadQueries(
            "findOwnDetailedRecords",
            new Object[] {actingUserId, nodeId, from, to},
            new Object[] {actingUserId, from, to, nodeId}
        );
        return asItemList(result);
    }

    private List<?> findVisibleDailyTotalsForOtherUser(
        UUID actingUserId,
        UUID targetUserId,
        UUID nodeId,
        LocalDate from,
        LocalDate to
    ) {
        Object result = invokeMcpReadQueries(
            "findVisibleDailyTotalsForOtherUser",
            new Object[] {actingUserId, targetUserId, nodeId, from, to},
            new Object[] {actingUserId, targetUserId, from, to, nodeId}
        );
        return asItemList(result);
    }

    private List<?> flattenTree(Object result) {
        List<Object> nodes = new ArrayList<>();
        if (result instanceof Collection<?> collection) {
            collection.forEach(item -> collectTreeNodes(item, nodes));
            return List.copyOf(nodes);
        }

        Optional<Object> maybeNodes = readOptionalProperty(result, "items", "nodes", "roots");
        if (maybeNodes.isPresent() && maybeNodes.orElseThrow() instanceof Collection<?> collection) {
            collection.forEach(item -> collectTreeNodes(item, nodes));
            return List.copyOf(nodes);
        }

        collectTreeNodes(result, nodes);
        return List.copyOf(nodes);
    }

    private void collectTreeNodes(Object node, List<Object> nodes) {
        nodes.add(node);
        Optional<Object> children = readOptionalProperty(node, "children");
        if (children.isPresent() && children.orElseThrow() instanceof Collection<?> collection) {
            collection.forEach(child -> collectTreeNodes(child, nodes));
        }
    }

    private boolean objectContainsTotalSeconds(Object target, long expectedTotalSeconds) {
        Optional<Object> directTotal = readOptionalProperty(target, "totalSeconds", "durationSeconds", "totalDurationSeconds");
        if (directTotal.isPresent() && directTotal.orElseThrow() instanceof Number number) {
            return number.longValue() == expectedTotalSeconds;
        }

        Optional<Object> nestedRows = readOptionalProperty(target, "buckets", "rows", "items");
        if (nestedRows.isPresent() && nestedRows.orElseThrow() instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> objectContainsTotalSeconds(item, expectedTotalSeconds));
        }

        return false;
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

    private Object invokeMcpReadQueries(String methodName, Object[]... argumentVariants) {
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
                    throw new AssertionError("Could not access McpReadQueries method " + methodName, ex);
                } catch (InvocationTargetException ex) {
                    throw new AssertionError(
                        "McpReadQueries method " + methodName + " threw an exception",
                        ex.getTargetException()
                    );
                }
            }
        }
        throw new AssertionError(
            "Expected McpReadQueries to expose compatible method " + methodName
                + "; available signatures: " + availableSignatures
        );
    }

    private Object readQueriesBean() {
        try {
            Class<?> readQueriesType = Class.forName("com.trawhile.repository.read.McpReadQueries");
            return applicationContext.getBean(readQueriesType);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected com.trawhile.repository.read.McpReadQueries to exist", ex);
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

        Optional<Object> items = readOptionalProperty(result, "items", "rows", "results", "records", "totals");
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
