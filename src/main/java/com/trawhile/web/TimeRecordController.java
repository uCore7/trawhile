package com.trawhile.web;

import com.trawhile.service.TimeRecordService;
import com.trawhile.web.api.TimeRecordsApi;
import com.trawhile.web.dto.CreateTimeRecordRequest;
import com.trawhile.web.dto.DuplicateTimeRecordRequest;
import com.trawhile.web.dto.UpdateTimeRecordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** /api/v1/time-records — manual record management (create, edit, delete, duplicate). */
@RestController
@RequestMapping("/api/v1")
public class TimeRecordController implements TimeRecordsApi {

    private final TimeRecordService timeRecordService;

    public TimeRecordController(TimeRecordService timeRecordService) {
        this.timeRecordService = timeRecordService;
    }

    @Override
    public ResponseEntity<com.trawhile.web.dto.TimeRecord> createTimeRecord(CreateTimeRecordRequest createTimeRecordRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(timeRecordService.createRetroactive(currentUserId(), createTimeRecordRequest));
    }

    @Override
    public ResponseEntity<Void> deleteTimeRecord(UUID recordId) {
        timeRecordService.deleteRecord(currentUserId(), recordId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<com.trawhile.web.dto.TimeRecord> duplicateTimeRecord(UUID recordId,
                                                                                DuplicateTimeRecordRequest duplicateTimeRecordRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(timeRecordService.duplicateRecord(currentUserId(), recordId, duplicateTimeRecordRequest));
    }

    @Override
    public ResponseEntity<com.trawhile.web.dto.TimeRecord> updateTimeRecord(UUID recordId,
                                                                            UpdateTimeRecordRequest updateTimeRecordRequest) {
        return ResponseEntity.ok(timeRecordService.editRecord(currentUserId(), recordId, updateTimeRecordRequest));
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
