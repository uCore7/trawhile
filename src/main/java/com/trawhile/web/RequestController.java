package com.trawhile.web;

import com.trawhile.service.RequestService;
import com.trawhile.web.api.RequestsApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/nodes/{nodeId}/requests — submit, view, and close requests. */
@RestController
@RequestMapping("/api/v1")
public class RequestController implements RequestsApi {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    // TODO: implement SR-F039.F01 (submit), SR-F041.F01 (view), SR-F042.F01 (close)
}
