package com.trawhile.service;

import com.trawhile.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DataExportService {

    private final NodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final NodeAuthorizationRepository authorizationRepository;

    public DataExportService(NodeRepository nodeRepository,
                             UserRepository userRepository,
                             TimeEntryRepository timeEntryRepository,
                             NodeAuthorizationRepository authorizationRepository) {
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.authorizationRepository = authorizationRepository;
    }

    // TODO: implement F1.12–F1.13 (CSV export, CSV import)
}
