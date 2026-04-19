package com.trawhile.web;

import com.trawhile.service.NodeService;
import com.trawhile.web.api.NodesApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/nodes — node CRUD and tree navigation.
 *
 * Endpoints:
 *   GET    /nodes/root                       — SR-F014.F01 getRootNode
 *   GET    /nodes/{nodeId}                   — SR-F014.F01 getNode
 *   PATCH  /nodes/{nodeId}                   — SR-F016.F01 updateNode (name, description, color, icon)
 *   POST   /nodes/{nodeId}/children          — SR-F015.F01 createChildNode
 *   PUT    /nodes/{nodeId}/children/order    — SR-F017.F01 reorderChildren
 *   GET    /nodes/{nodeId}/logo              — SR-F016.F01 getNodeLogo
 *   PUT    /nodes/{nodeId}/logo              — SR-F016.F01 uploadNodeLogo (multipart, max 256 KB)
 *   DELETE /nodes/{nodeId}/logo              — SR-F016.F01 deleteNodeLogo
 *   POST   /nodes/{nodeId}/deactivate        — SR-F018.F01 deactivateNode
 *   POST   /nodes/{nodeId}/reactivate        — SR-F019.F01 reactivateNode
 *   POST   /nodes/{nodeId}/move              — SR-F020.F01 moveNode
 */
@RestController
@RequestMapping("/api/v1")
public class NodeController implements NodesApi {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    // TODO: implement all endpoints listed above
    // Logo upload: validate MIME type (image/png, image/jpeg, image/svg+xml, image/webp) and size (<= 256 KB)
    // Logo GET: return node.logo() as the appropriate MIME type; 404 if null
    // On node update (colour, icon, logo): dispatch SseEvent.QUICK_ACCESS to affected users
}
