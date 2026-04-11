package com.trawhile.web;

import com.trawhile.service.NodeService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/nodes — node CRUD and tree navigation.
 *
 * Endpoints:
 *   GET    /nodes/root                       — SR-016 getRootNode
 *   GET    /nodes/{nodeId}                   — SR-016 getNode
 *   PATCH  /nodes/{nodeId}                   — SR-018 updateNode (name, description, color, icon)
 *   POST   /nodes/{nodeId}/children          — SR-017 createChildNode
 *   PUT    /nodes/{nodeId}/children/order    — SR-019 reorderChildren
 *   GET    /nodes/{nodeId}/logo              — SR-018 getNodeLogo
 *   PUT    /nodes/{nodeId}/logo              — SR-018 uploadNodeLogo (multipart, max 256 KB)
 *   DELETE /nodes/{nodeId}/logo              — SR-018 deleteNodeLogo
 *   POST   /nodes/{nodeId}/deactivate        — SR-020 deactivateNode
 *   POST   /nodes/{nodeId}/reactivate        — SR-021 reactivateNode
 *   POST   /nodes/{nodeId}/move              — SR-022 moveNode
 */
@RestController
@RequestMapping("/api/v1/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    // TODO: implement all endpoints listed above
    // Logo upload: validate MIME type (image/png, image/jpeg, image/gif, image/webp) and size (<= 256 KB)
    // Logo GET: return node.logo() as the appropriate MIME type; 404 if null
    // On node update (colour, icon, logo): dispatch SseEvent.QUICK_ACCESS to affected users
}
