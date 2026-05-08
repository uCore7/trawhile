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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestReadQueriesIT extends BaseIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Tag("persistence-sensitive-reads")
    void findVisibleRequestsReturnsOnlyRequestsInsideRequestedVisibleSubtree() {
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID visibleParentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Parent");
        UUID visibleChildId = TestFixtures.insertNode(jdbc, visibleParentId, "Visible Child");
        UUID hiddenSiblingId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Sibling");
        UUID hiddenDescendantId = TestFixtures.insertNode(jdbc, hiddenSiblingId, "Hidden Descendant");
        TestFixtures.grantAuth(jdbc, viewerId, visibleParentId, "view");

        UUID visibleParentRequestId = insertRequest(requesterId, visibleParentId, "grant_authorization", "parent");
        UUID visibleChildRequestId = insertRequest(requesterId, visibleChildId, "create_child", "child");
        UUID hiddenSiblingRequestId = insertRequest(requesterId, hiddenSiblingId, "free_text", "hidden sibling");
        UUID hiddenDescendantRequestId = insertRequest(requesterId, hiddenDescendantId, "free_text", "hidden child");

        List<?> visibleRequests = findVisibleRequests(viewerId, visibleParentId);

        assertThat(visibleRequests)
            .extracting(this::requestId)
            .containsExactlyInAnyOrder(visibleParentRequestId, visibleChildRequestId)
            .doesNotContain(hiddenSiblingRequestId, hiddenDescendantRequestId);
        assertThat(visibleRequests)
            .extracting(this::requestNodeId)
            .containsExactlyInAnyOrder(visibleParentId, visibleChildId)
            .doesNotContain(hiddenSiblingId, hiddenDescendantId);
    }

    @Test
    @Tag("persistence-sensitive-reads")
    void findVisibleRequestsReturnsEmptyWhenRequestedSubtreeRootIsInvisible() {
        UUID viewerId = TestFixtures.insertUserWithProfile(jdbc, "Viewer");
        UUID requesterId = TestFixtures.insertUserWithProfile(jdbc, "Requester");
        UUID visibleBranchId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Branch");
        UUID hiddenBranchId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Branch");
        UUID hiddenChildId = TestFixtures.insertNode(jdbc, hiddenBranchId, "Hidden Child");
        TestFixtures.grantAuth(jdbc, viewerId, visibleBranchId, "view");
        insertRequest(requesterId, hiddenBranchId, "free_text", "hidden");
        insertRequest(requesterId, hiddenChildId, "free_text", "also hidden");

        List<?> hiddenRequests = findVisibleRequests(viewerId, hiddenBranchId);

        assertThat(hiddenRequests).isEmpty();
    }

    private List<?> findVisibleRequests(UUID actingUserId, UUID nodeId) {
        Object result = invokeRequestReadQueries("findVisibleRequests", new Object[] {actingUserId, nodeId});
        return asItemList(result);
    }

    private UUID requestId(Object row) {
        return (UUID) readProperty(row, "id", "requestId");
    }

    private UUID requestNodeId(Object row) {
        return (UUID) readProperty(row, "nodeId");
    }

    private UUID insertRequest(UUID requesterId, UUID nodeId, String template, String body) {
        UUID requestId = UUID.randomUUID();
        jdbc.update(
            """
                INSERT INTO requests (id, requester_id, node_id, template, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            requestId,
            requesterId,
            nodeId,
            template,
            body,
            OffsetDateTime.now().withNano(0)
        );
        return requestId;
    }

    private Object invokeRequestReadQueries(String methodName, Object[]... argumentVariants) {
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
                    throw new AssertionError("Could not access RequestReadQueries method " + methodName, ex);
                } catch (InvocationTargetException ex) {
                    throw new AssertionError(
                        "RequestReadQueries method " + methodName + " threw an exception",
                        ex.getTargetException()
                    );
                }
            }
        }
        throw new AssertionError(
            "Expected RequestReadQueries to expose compatible method " + methodName
                + "; available signatures: " + availableSignatures
        );
    }

    private Object readQueriesBean() {
        try {
            Class<?> readQueriesType = Class.forName("com.trawhile.repository.read.RequestReadQueries");
            return applicationContext.getBean(readQueriesType);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected com.trawhile.repository.read.RequestReadQueries to exist", ex);
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

        Optional<Object> items = readOptionalProperty(result, "items", "rows", "results", "requests");
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
