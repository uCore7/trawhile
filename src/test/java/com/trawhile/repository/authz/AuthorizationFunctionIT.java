package com.trawhile.repository.authz;

import com.trawhile.BaseIT;
import com.trawhile.TestFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationFunctionIT extends BaseIT {

    @Test
    @Tag("persistence-foundation")
    void directAuthorizationMakesOnlyThatNodeVisible() {
        UUID viewerId = TestFixtures.insertUser(jdbc);
        UUID nodeId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Directly Granted");
        UUID siblingId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Sibling");
        TestFixtures.grantAuth(jdbc, viewerId, nodeId, "view");

        List<UUID> visibleNodeIds = visibleNodeIds(viewerId);

        assertThat(visibleNodeIds)
            .containsExactly(nodeId)
            .doesNotContain(TestFixtures.ROOT_NODE_ID, siblingId);
    }

    @Test
    @Tag("persistence-foundation")
    void ancestorAuthorizationMakesDescendantsVisibleButNotOtherBranches() {
        UUID viewerId = TestFixtures.insertUser(jdbc);
        UUID ancestorId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Ancestor");
        UUID childId = TestFixtures.insertNode(jdbc, ancestorId, "Child");
        UUID grandchildId = TestFixtures.insertNode(jdbc, childId, "Grandchild");
        UUID siblingBranchId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Sibling Branch");
        TestFixtures.grantAuth(jdbc, viewerId, ancestorId, "track");

        List<UUID> visibleNodeIds = visibleNodeIds(viewerId);

        assertThat(visibleNodeIds)
            .containsExactlyInAnyOrder(ancestorId, childId, grandchildId)
            .doesNotContain(TestFixtures.ROOT_NODE_ID, siblingBranchId);
    }

    @Test
    @Tag("persistence-foundation")
    void userWithNoAuthorizationSeesNoNodesWhileAuthorizedUserSeesGrantedSubtree() {
        UUID authorizedUserId = TestFixtures.insertUser(jdbc);
        UUID unauthorizedUserId = TestFixtures.insertUser(jdbc);
        UUID ancestorId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Visible Ancestor");
        UUID childId = TestFixtures.insertNode(jdbc, ancestorId, "Visible Child");
        UUID hiddenSiblingId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Hidden Sibling");
        TestFixtures.grantAuth(jdbc, authorizedUserId, ancestorId, "view");

        List<UUID> authorizedVisibleNodeIds = visibleNodeIds(authorizedUserId);
        List<UUID> unauthorizedVisibleNodeIds = visibleNodeIds(unauthorizedUserId);

        assertThat(authorizedVisibleNodeIds)
            .containsExactlyInAnyOrder(ancestorId, childId)
            .doesNotContain(hiddenSiblingId, TestFixtures.ROOT_NODE_ID);
        assertThat(unauthorizedVisibleNodeIds).isEmpty();
    }

    @Test
    @Tag("persistence-foundation")
    void overlappingAncestorGrantsDoNotProduceDuplicateVisibleNodes() {
        UUID viewerId = TestFixtures.insertUser(jdbc);
        UUID ancestorId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Ancestor");
        UUID childId = TestFixtures.insertNode(jdbc, ancestorId, "Child");
        UUID grandchildId = TestFixtures.insertNode(jdbc, childId, "Grandchild");
        UUID siblingId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Sibling");
        TestFixtures.grantAuth(jdbc, viewerId, ancestorId, "view");
        TestFixtures.grantAuth(jdbc, viewerId, childId, "admin");

        List<UUID> visibleNodeIds = visibleNodeIds(viewerId);

        assertThat(visibleNodeIds)
            .containsExactlyInAnyOrder(ancestorId, childId, grandchildId)
            .doesNotContain(siblingId, TestFixtures.ROOT_NODE_ID)
            .doesNotHaveDuplicates();
        assertThat(visibleNodeIds.stream().filter(grandchildId::equals).count()).isOne();
    }

    @Test
    @Tag("persistence-foundation")
    void rootGrantMakesRootAndDescendantsVisibleWhileChildOnlyGrantDoesNotRevealRoot() {
        UUID rootViewerId = TestFixtures.insertUser(jdbc);
        UUID childOnlyViewerId = TestFixtures.insertUser(jdbc);
        UUID departmentId = TestFixtures.insertNode(jdbc, TestFixtures.ROOT_NODE_ID, "Department");
        UUID projectId = TestFixtures.insertNode(jdbc, departmentId, "Project");
        UUID taskId = TestFixtures.insertNode(jdbc, projectId, "Task");
        TestFixtures.grantAuth(jdbc, rootViewerId, TestFixtures.ROOT_NODE_ID, "admin");
        TestFixtures.grantAuth(jdbc, childOnlyViewerId, departmentId, "view");

        List<UUID> rootVisibleNodeIds = visibleNodeIds(rootViewerId);
        List<UUID> childOnlyVisibleNodeIds = visibleNodeIds(childOnlyViewerId);

        assertThat(rootVisibleNodeIds)
            .containsExactlyInAnyOrder(TestFixtures.ROOT_NODE_ID, departmentId, projectId, taskId)
            .doesNotHaveDuplicates();
        assertThat(childOnlyVisibleNodeIds)
            .containsExactlyInAnyOrder(departmentId, projectId, taskId)
            .doesNotContain(TestFixtures.ROOT_NODE_ID);
    }

    private List<UUID> visibleNodeIds(UUID userId) {
        return jdbc.query(
            "SELECT * FROM visible_nodes(?)",
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            userId
        );
    }
}
