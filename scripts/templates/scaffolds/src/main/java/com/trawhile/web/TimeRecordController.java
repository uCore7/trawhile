package com.trawhile.web;

import com.trawhile.service.TimeRecordService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/time-records — manual record management (create, edit, delete, duplicate). */
@RestController
@RequestMapping("/api/v1/time-records")
public class TimeRecordController {

    private final TimeRecordService timeRecordService;

    public TimeRecordController(TimeRecordService timeRecordService) {
        this.timeRecordService = timeRecordService;
    }

    // TODO: implement SR-F025.F01 (list records), SR-F031.F01 (create retroactive), SR-F032.F01 (edit), SR-F033.F01 (delete), SR-F034.F01 (duplicate)
}
