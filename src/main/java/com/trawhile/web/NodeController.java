package com.trawhile.web;

import com.trawhile.service.NodeService;
import com.trawhile.web.api.NodesApi;
import com.trawhile.web.dto.CreateChildNodeRequest;
import com.trawhile.web.dto.MoveNodeRequest;
import com.trawhile.web.dto.Node;
import com.trawhile.web.dto.ReorderChildrenRequest;
import com.trawhile.web.dto.UpdateNodeRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

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

    private static final int MAX_LOGO_BYTES = 256 * 1024;
    private static final Set<String> ALLOWED_LOGO_MIME_TYPES = Set.of(
        MediaType.IMAGE_PNG_VALUE,
        MediaType.IMAGE_JPEG_VALUE,
        "image/svg+xml",
        "image/webp"
    );

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Override
    public ResponseEntity<Node> createChildNode(UUID nodeId, CreateChildNodeRequest createChildNodeRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(nodeService.createChild(currentUserId(), nodeId, createChildNodeRequest));
    }

    @Override
    public ResponseEntity<Node> deactivateNode(UUID nodeId) {
        return ResponseEntity.ok(nodeService.deactivateNode(currentUserId(), nodeId));
    }

    @Override
    public ResponseEntity<Node> deleteNodeLogo(UUID nodeId) {
        return ResponseEntity.ok(nodeService.deleteLogo(currentUserId(), nodeId));
    }

    @Override
    public ResponseEntity<Node> getNode(UUID nodeId) {
        return ResponseEntity.ok(nodeService.getNode(currentUserId(), nodeId));
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getNodeLogo(UUID nodeId) {
        com.trawhile.domain.Node node = nodeService.getLogo(currentUserId(), nodeId);
        ByteArrayResource resource = new ByteArrayResource(node.logo());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, node.logoMimeType())
            .body(resource);
    }

    @Override
    public ResponseEntity<Node> getRootNode() {
        return ResponseEntity.ok(nodeService.getRootNode(currentUserId()));
    }

    @Override
    public ResponseEntity<Node> moveNode(UUID nodeId, MoveNodeRequest moveNodeRequest) {
        return ResponseEntity.ok(nodeService.moveNode(currentUserId(), nodeId, moveNodeRequest));
    }

    @Override
    public ResponseEntity<Node> reactivateNode(UUID nodeId) {
        return ResponseEntity.ok(nodeService.reactivateNode(currentUserId(), nodeId));
    }

    @Override
    public ResponseEntity<Void> reorderChildren(UUID nodeId, ReorderChildrenRequest reorderChildrenRequest) {
        nodeService.reorderChildren(currentUserId(), nodeId, reorderChildrenRequest);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Node> updateNode(UUID nodeId, UpdateNodeRequest updateNodeRequest) {
        return ResponseEntity.ok(nodeService.updateNode(currentUserId(), nodeId, updateNodeRequest));
    }

    @Override
    public ResponseEntity<Node> uploadNodeLogo(UUID nodeId, MultipartFile logo) {
        validateLogo(logo);
        try {
            return ResponseEntity.ok(nodeService.uploadLogo(
                currentUserId(),
                nodeId,
                logo.getBytes(),
                logo.getContentType()
            ));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded logo", ex);
        }
    }

    private void validateLogo(MultipartFile logo) {
        if (logo.getSize() > MAX_LOGO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo must be 256 KB or smaller");
        }
        if (!ALLOWED_LOGO_MIME_TYPES.contains(logo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported logo MIME type");
        }
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
