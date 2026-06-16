package com.trawhile.service.administration;

public class InvitationConflictException extends RuntimeException {

    private final String conflictCode;

    public InvitationConflictException(String conflictCode) {
        super(conflictCode);
        this.conflictCode = conflictCode;
    }

    public String getConflictCode() {
        return conflictCode;
    }
}
