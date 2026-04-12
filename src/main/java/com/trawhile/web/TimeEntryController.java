package com.trawhile.web;

import com.trawhile.service.TimeEntryService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/time-entries — manual entry management (create, edit, delete, duplicate). */
@RestController
@RequestMapping("/api/v1/time-entries")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;

    public TimeEntryController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    // TODO: implement SR-F025.F01 (list entries), SR-F031.F01 (create retroactive), SR-F032.F01 (edit), SR-F033.F01 (delete), SR-F034.F01 (duplicate)
}
