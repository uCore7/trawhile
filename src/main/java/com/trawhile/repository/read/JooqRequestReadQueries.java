package com.trawhile.repository.read;

import com.trawhile.jooq.tables.Requests;
import com.trawhile.jooq.tables.UserProfile;
import com.trawhile.repository.authz.AuthorizationFunctions;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static com.trawhile.jooq.tables.Requests.REQUESTS;
import static com.trawhile.jooq.tables.UserProfile.USER_PROFILE;

@Repository
public class JooqRequestReadQueries implements RequestReadQueries {

    private final DSLContext dsl;
    private final AuthorizationFunctions authorizationFunctions;

    public JooqRequestReadQueries(DSLContext dsl, AuthorizationFunctions authorizationFunctions) {
        this.dsl = dsl;
        this.authorizationFunctions = authorizationFunctions;
    }

    @Override
    public List<RequestRow> findVisibleRequests(UUID actingUserId, UUID subtreeRootNodeId) {
        Requests request = REQUESTS;
        UserProfile requesterProfile = USER_PROFILE.as("requester_profile");
        UserProfile resolverProfile = USER_PROFILE.as("resolver_profile");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);
        Table<Record1<UUID>> subtreeNodes = ReadQueryScopes.subtreeNodesTable();
        Field<UUID> subtreeNodeId = ReadQueryScopes.subtreeNodeId(subtreeNodes);

        return dsl.withRecursive(ReadQueryScopes.subtreeNodes(subtreeRootNodeId))
            .select(
                request.ID,
                request.REQUESTER_ID,
                requesterProfile.NAME.as("requester_name"),
                request.NODE_ID,
                request.TEMPLATE,
                request.BODY,
                request.STATUS,
                request.CREATED_AT,
                request.RESOLVED_AT,
                request.RESOLVED_BY,
                resolverProfile.NAME.as("resolved_by_name")
            )
            .from(request)
            .join(visibleNodes).on(request.NODE_ID.eq(visibleNodeId))
            .join(subtreeNodes).on(request.NODE_ID.eq(subtreeNodeId))
            .leftJoin(requesterProfile).on(request.REQUESTER_ID.eq(requesterProfile.USER_ID))
            .leftJoin(resolverProfile).on(request.RESOLVED_BY.eq(resolverProfile.USER_ID))
            .where(DSL.noCondition())
            .orderBy(request.CREATED_AT.desc(), request.ID.desc())
            .fetch(this::mapRow);
    }

    @Override
    public List<RequestRow> findVisibleRequestsOnNode(UUID actingUserId, UUID nodeId) {
        Requests request = REQUESTS;
        UserProfile requesterProfile = USER_PROFILE.as("requester_profile");
        UserProfile resolverProfile = USER_PROFILE.as("resolver_profile");
        Table<Record1<UUID>> visibleNodes = authorizationFunctions.visibleNodes(actingUserId);
        Field<UUID> visibleNodeId = authorizationFunctions.visibleNodeId(visibleNodes);

        return dsl.select(
                request.ID,
                request.REQUESTER_ID,
                requesterProfile.NAME.as("requester_name"),
                request.NODE_ID,
                request.TEMPLATE,
                request.BODY,
                request.STATUS,
                request.CREATED_AT,
                request.RESOLVED_AT,
                request.RESOLVED_BY,
                resolverProfile.NAME.as("resolved_by_name")
            )
            .from(request)
            .join(visibleNodes).on(request.NODE_ID.eq(visibleNodeId))
            .leftJoin(requesterProfile).on(request.REQUESTER_ID.eq(requesterProfile.USER_ID))
            .leftJoin(resolverProfile).on(request.RESOLVED_BY.eq(resolverProfile.USER_ID))
            .where(request.NODE_ID.eq(nodeId))
            .orderBy(request.CREATED_AT.desc(), request.ID.desc())
            .fetch(this::mapRow);
    }

    private RequestRow mapRow(org.jooq.Record record) {
        return new RequestRow(
            record.get(REQUESTS.ID),
            record.get(REQUESTS.REQUESTER_ID),
            record.get("requester_name", String.class),
            record.get(REQUESTS.NODE_ID),
            record.get(REQUESTS.TEMPLATE),
            record.get(REQUESTS.BODY),
            record.get(REQUESTS.STATUS),
            record.get(REQUESTS.CREATED_AT),
            record.get(REQUESTS.RESOLVED_AT),
            record.get(REQUESTS.RESOLVED_BY),
            record.get("resolved_by_name", String.class)
        );
    }
}
