package com.trawhile.service;

import com.trawhile.repository.NodeAuthorizationRepository;
import com.trawhile.repository.NodeRepository;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NodeService {

    private final NodeRepository nodeRepository;
    private final NodeAuthorizationRepository nodeAuthorizationRepository;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;

    public NodeService(NodeRepository nodeRepository,
                       NodeAuthorizationRepository nodeAuthorizationRepository,
                       AuthorizationService authorizationService,
                       SseDispatcher sseDispatcher) {
        this.nodeRepository = nodeRepository;
        this.nodeAuthorizationRepository = nodeAuthorizationRepository;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement SR-F014.F01–SR-F020.F01 (node CRUD), SR-F021.F01–SR-F023.F01 (authorization management)
}
