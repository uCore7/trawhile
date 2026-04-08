package com.trawhile.service;

import com.trawhile.repository.RequestRepository;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RequestService {

    private final RequestRepository requestRepository;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;

    public RequestService(RequestRepository requestRepository,
                          AuthorizationService authorizationService,
                          SseDispatcher sseDispatcher) {
        this.requestRepository = requestRepository;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement F5.1–F5.3 (submit, view, close requests)
}
