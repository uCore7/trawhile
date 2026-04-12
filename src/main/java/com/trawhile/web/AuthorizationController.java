package com.trawhile.web;

import com.trawhile.service.NodeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/nodes/{nodeId}/authorizations — grant/revoke/view node authorization assignments. */
@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/authorizations")
public class AuthorizationController {

    private final NodeService nodeService;

    public AuthorizationController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    // TODO: implement F2.8–F2.10
}
