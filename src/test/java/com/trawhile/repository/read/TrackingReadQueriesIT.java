package com.trawhile.repository.read;

import com.trawhile.BaseIT;
import com.trawhile.TestFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

class TrackingReadQueriesIT extends BaseIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Tag("persistence-sensitive-reads")
    void findOwnTrackingStatusDoesNotExposeAnotherUsersActiveRecord() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Caller");
        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Other");
        UUID otherNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Other Task");
        UUID otherRecordId = insertTimeRecord(
            otherUserId,
            otherNodeId,
            OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(20).withNano(0),
            null,
            "UTC",
            "Other active record"
        );

        Object result = findOwnTrackingStatus(actingUserId);

        if (result instanceof Optional<?> optional) {
            assertThat(optional).isEmpty();
            return;
        }

        assertThat(readProperty(result, "active")).isEqualTo(false);
        assertThat(readOptionalProperty(result, "recordId", "id")).isEmpty();
        assertThat(readOptionalProperty(result, "nodeId")).isEmpty();
        assertThat(readOptionalProperty(result, "userId", "ownerId"))
            .satisfies(optionalValue -> assertThat(optionalValue.orElse(null)).isNotEqualTo(otherUserId));
        assertThat(result.toString())
            .doesNotContain(otherRecordId.toString())
            .doesNotContain(otherNodeId.toString());
    }

    @Test
    @Tag("persistence-sensitive-reads")
    void findOwnTrackingHistoryReturnsOnlyCallersRowsInDescendingStartOrder() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Caller");
        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Other");
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Tracked Task");
        OffsetDateTime base = OffsetDateTime.of(2024, 6, 11, 8, 0, 0, 0, ZoneOffset.UTC);
        UUID olderRecordId = insertTimeRecord(
            actingUserId,
            nodeId,
            base,
            base.plusHours(1),
            "UTC",
            "Older own record"
        );
        UUID newerRecordId = insertTimeRecord(
            actingUserId,
            nodeId,
            base.plusHours(3),
            base.plusHours(4),
            "UTC",
            "Newer own record"
        );
        insertTimeRecord(
            otherUserId,
            nodeId,
            base.plusHours(5),
            base.plusHours(6),
            "UTC",
            "Other user record"
        );

        List<?> history = findOwnTrackingHistory(actingUserId, 10, 0);

        assertThat(history)
            .extracting(this::recordId)
            .containsExactly(newerRecordId, olderRecordId);
        assertThat(history)
            .extracting(row -> readProperty(row, "userId", "ownerId"))
            .containsOnly(actingUserId);
    }

    @Test
    @Tag("persistence-sensitive-reads")
    void findOwnQuickAccessReturnsOnlyCallersEntriesAndDoesNotLeakHiddenNodeMetadata() {
        UUID actingUserId = TestFixtures.insertUserWithProfile(jdbc, "Caller");
        UUID otherUserId = TestFixtures.insertUserWithProfile(jdbc, "Other");
        UUID visibleNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Quick Access");
        UUID hiddenNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Quick Access");
        UUID othersNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Other Users Node");
        TestFixtures.grantAuth(jdbc, actingUserId, visibleNodeId, "view");
        TestFixtures.grantAuth(jdbc, otherUserId, othersNodeId, "view");
        insertQuickAccess(actingUserId, visibleNodeId, 0);
        insertQuickAccess(actingUserId, hiddenNodeId, 1);
        insertQuickAccess(otherUserId, othersNodeId, 0);

        List<?> quickAccessEntries = findOwnQuickAccess(actingUserId);

        assertThat(quickAccessEntries)
            .extracting(entry -> readProperty(entry, "nodeId"))
            .contains(visibleNodeId)
            .doesNotContain(othersNodeId);
        assertThat(quickAccessEntries)
            .extracting(entry -> readOptionalProperty(entry, "name", "nodeName").orElse(null))
            .contains("Visible Quick Access")
            .doesNotContain("Hidden Quick Access", "Other Users Node");
    }

    private Object findOwnTrackingStatus(UUID actingUserId) {
        return invokeTrackingReadQueries("findOwnTrackingStatus", new Object[] {actingUserId});
    }

    private List<?> findOwnTrackingHistory(UUID actingUserId, int limit, int offset) {
        Object result = invokeTrackingReadQueries("findOwnTrackingHistory", new Object[] {actingUserId, limit, offset});
        return asItemList(result);
    }

    private List<?> findOwnQuickAccess(UUID actingUserId) {
        Object result = invokeTrackingReadQueries("findOwnQuickAccess", new Object[] {actingUserId});
        return asItemList(result);
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

    private void insertQuickAccess(UUID userId, UUID nodeId, int sortOrder) {
        UUID profileId = jdbc.queryForObject(
            "SELECT id FROM user_profile WHERE user_id = ?",
            UUID.class,
            userId
        );
        jdbc.update(
            "INSERT INTO quick_access (id, profile_id, node_id, sort_order) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            profileId,
            nodeId,
            sortOrder
        );
    }

    private Object invokeTrackingReadQueries(String methodName, Object[]... argumentVariants) {
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
                    throw new AssertionError("Could not access TrackingReadQueries method " + methodName, ex);
                } catch (InvocationTargetException ex) {
                    throw new AssertionError(
                        "TrackingReadQueries method " + methodName + " threw an exception",
                        ex.getTargetException()
                    );
                }
            }
        }
        throw new AssertionError(
            "Expected TrackingReadQueries to expose compatible method " + methodName
                + "; available signatures: " + availableSignatures
        );
    }

    private Object readQueriesBean() {
        try {
            Class<?> readQueriesType = Class.forName("com.trawhile.repository.read.TrackingReadQueries");
            return applicationContext.getBean(readQueriesType);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected com.trawhile.repository.read.TrackingReadQueries to exist", ex);
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

        Optional<Object> items = readOptionalProperty(result, "items", "rows", "results", "history", "entries");
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
