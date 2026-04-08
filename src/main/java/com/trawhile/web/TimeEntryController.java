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

    // TODO: implement F3.2, F3.10–F3.13
}
