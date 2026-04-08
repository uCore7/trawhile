package com.trawhile.web;

import com.trawhile.service.NodeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/nodes — node CRUD and tree navigation. */
@RestController
@RequestMapping("/api/v1/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    // TODO: implement F2.1–F2.7
}
