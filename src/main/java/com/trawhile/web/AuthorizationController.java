package com.trawhile.web;

import com.trawhile.service.NodeService;
import com.trawhile.web.api.AuthorizationsApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/nodes/{nodeId}/authorizations — grant/revoke/view node authorization assignments. */
@RestController
@RequestMapping("/api/v1")
public class AuthorizationController implements AuthorizationsApi {

    private final NodeService nodeService;

    public AuthorizationController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    // TODO: implement SR-F021.F01 (grant authorization), SR-F022.F01 (revoke authorization), SR-F023.F01 (view authorizations)
}
