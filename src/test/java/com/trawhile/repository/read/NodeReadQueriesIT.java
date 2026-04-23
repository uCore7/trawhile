package com.trawhile.repository.read;

import com.trawhile.BaseIT;
import com.trawhile.TestFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeReadQueriesIT extends BaseIT {

    private static final byte[] PNG_BYTES = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
        (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, 0x00, 0x00,
        0x02, 0x05, 0x01, 0x02, (byte) 0xA7, 0x69, (byte) 0xE6, (byte) 0xD5,
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @Tag("persistence-foundation")
    void findVisibleRootNodeSummaryReturnsRootOnlyForCallersWhoCanSeeRoot() {
        UUID rootViewerId = TestFixtures.insertUser(jdbc);
        UUID childOnlyViewerId = TestFixtures.insertUser(jdbc);
        UUID childId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Child");
        TestFixtures.grantAuth(jdbc, rootViewerId, TestFixtures.ROOT_NODE_ID, "view");
        TestFixtures.grantAuth(jdbc, childOnlyViewerId, childId, "view");

        Optional<?> visibleRoot = findVisibleRootNodeSummary(rootViewerId);
        Optional<?> hiddenRoot = findVisibleRootNodeSummary(childOnlyViewerId);

        assertThat(visibleRoot).isPresent();
        assertThat(readProperty(visibleRoot.orElseThrow(), "id")).isEqualTo(TestFixtures.ROOT_NODE_ID);
        assertThat(readProperty(visibleRoot.orElseThrow(), "name")).isEqualTo("root");
        assertThat(hiddenRoot).isEmpty();
    }

    @Test
    @Tag("persistence-foundation")
    void findVisibleNodeSummaryReturnsRequestedNodeWhenVisibleAndEmptyWhenNotVisible() {
        UUID viewerId = TestFixtures.insertUser(jdbc);
        UUID visibleNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible");
        UUID hiddenNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden");
        TestFixtures.grantAuth(jdbc, viewerId, visibleNodeId, "track");

        Optional<?> visibleSummary = findVisibleNodeSummary(viewerId, visibleNodeId);
        Optional<?> hiddenSummary = findVisibleNodeSummary(viewerId, hiddenNodeId);

        assertThat(visibleSummary).isPresent();
        assertThat(readProperty(visibleSummary.orElseThrow(), "id")).isEqualTo(visibleNodeId);
        assertThat(readProperty(visibleSummary.orElseThrow(), "name")).isEqualTo("Visible");
        assertThat(hiddenSummary).isEmpty();
    }

    @Test
    @Tag("persistence-foundation")
    void findVisibleChildrenReturnsOnlyDirectVisibleChildrenOrderedBySortOrder() {
        UUID viewerId = TestFixtures.insertUser(jdbc);
        UUID hiddenChildId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Child");
        UUID visibleChildLaterId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Child Later");
        UUID visibleChildFirstId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Child First");
        UUID hiddenGrandchildId = TestFixtures.insertNode(jdbc, hiddenChildId, "Hidden Grandchild");
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 0, hiddenChildId);
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 20, visibleChildLaterId);
        jdbc.update("UPDATE nodes SET sort_order = ? WHERE id = ?", 10, visibleChildFirstId);
        TestFixtures.grantAuth(jdbc, viewerId, visibleChildLaterId, "view");
        TestFixtures.grantAuth(jdbc, viewerId, visibleChildFirstId, "admin");
        TestFixtures.grantAuth(jdbc, viewerId, hiddenGrandchildId, "track");

        List<?> visibleChildren = findVisibleChildren(viewerId, TestFixtures.ROOT_NODE_ID);

        assertThat(visibleChildren)
            .extracting(child -> readProperty(child, "id"))
            .containsExactly(visibleChildFirstId, visibleChildLaterId)
            .doesNotContain(hiddenChildId, hiddenGrandchildId);
    }

    @Test
    @Tag("persistence-foundation")
    void findVisibleNodeContentReturnsScopedPayloadAndEmptyForInvisibleOrMissingContent() {
        UUID viewerId = TestFixtures.insertUser(jdbc);
        UUID visibleLogoNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Logo");
        UUID hiddenLogoNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Logo");
        UUID visibleNoLogoNodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible No Logo");
        jdbc.update(
            "UPDATE nodes SET logo = ?, logo_mime_type = ? WHERE id = ?",
            PNG_BYTES,
            "image/png",
            visibleLogoNodeId
        );
        jdbc.update(
            "UPDATE nodes SET logo = ?, logo_mime_type = ? WHERE id = ?",
            PNG_BYTES,
            "image/png",
            hiddenLogoNodeId
        );
        TestFixtures.grantAuth(jdbc, viewerId, visibleLogoNodeId, "view");
        TestFixtures.grantAuth(jdbc, viewerId, visibleNoLogoNodeId, "view");

        Optional<?> visibleContent = findVisibleNodeContent(viewerId, visibleLogoNodeId);
        Optional<?> hiddenContent = findVisibleNodeContent(viewerId, hiddenLogoNodeId);
        Optional<?> missingContent = findVisibleNodeContent(viewerId, visibleNoLogoNodeId);

        assertThat(visibleContent).isPresent();
        assertThat((byte[]) readProperty(visibleContent.orElseThrow(), "logo", "payload", "logoPayload"))
            .containsExactly(PNG_BYTES);
        assertThat(readProperty(visibleContent.orElseThrow(), "logoMimeType", "mimeType", "contentType"))
            .isEqualTo("image/png");
        assertThat(hiddenContent).isEmpty();
        assertThat(missingContent).isEmpty();
    }

    private Optional<?> findVisibleRootNodeSummary(UUID actingUserId) {
        Object result = invokeNodeReadQueries("findVisibleRootNodeSummary", actingUserId);
        assertThat(result).isInstanceOf(Optional.class);
        return (Optional<?>) result;
    }

    private Optional<?> findVisibleNodeSummary(UUID actingUserId, UUID nodeId) {
        Object result = invokeNodeReadQueries("findVisibleNodeSummary", actingUserId, nodeId);
        assertThat(result).isInstanceOf(Optional.class);
        return (Optional<?>) result;
    }

    private List<?> findVisibleChildren(UUID actingUserId, UUID parentId) {
        Object result = invokeNodeReadQueries("findVisibleChildren", actingUserId, parentId);
        assertThat(result).isInstanceOf(Collection.class);
        return List.copyOf((Collection<?>) result);
    }

    private Optional<?> findVisibleNodeContent(UUID actingUserId, UUID nodeId) {
        Object result = invokeNodeReadQueries("findVisibleNodeContent", actingUserId, nodeId);
        assertThat(result).isInstanceOf(Optional.class);
        return (Optional<?>) result;
    }

    private Object invokeNodeReadQueries(String methodName, Object... args) {
        Object bean = nodeReadQueriesBean();
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }

        try {
            Method method = bean.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(bean, args);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Expected NodeReadQueries to expose method " + methodName, ex);
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Could not access NodeReadQueries method " + methodName, ex);
        } catch (InvocationTargetException ex) {
            throw new AssertionError(
                "NodeReadQueries method " + methodName + " threw an exception",
                ex.getTargetException()
            );
        }
    }

    private Object nodeReadQueriesBean() {
        try {
            Class<?> nodeReadQueriesType = Class.forName("com.trawhile.repository.read.NodeReadQueries");
            return applicationContext.getBean(nodeReadQueriesType);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected com.trawhile.repository.read.NodeReadQueries to exist", ex);
        }
    }

    private Object readProperty(Object target, String... candidateNames) {
        if (target instanceof Map<?, ?> map) {
            for (String candidateName : candidateNames) {
                if (map.containsKey(candidateName)) {
                    return map.get(candidateName);
                }
            }
        }

        for (String candidateName : candidateNames) {
            Method accessor = findAccessor(target.getClass(), candidateName);
            if (accessor != null) {
                try {
                    return accessor.invoke(target);
                } catch (IllegalAccessException ex) {
                    throw new AssertionError("Could not read property " + candidateName, ex);
                } catch (InvocationTargetException ex) {
                    throw new AssertionError("Accessor for property " + candidateName + " threw an exception", ex);
                }
            }
        }

        throw new AssertionError(
            "Could not read any of properties " + List.of(candidateNames) + " from " + target.getClass().getName()
        );
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
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
