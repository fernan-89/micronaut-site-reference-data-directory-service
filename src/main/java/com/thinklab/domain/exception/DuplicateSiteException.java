package com.thinklab.domain.exception;

/**
 * Domain Exception: Thrown when an Site is initiated with a serial number that already exists
 * within the same Organisation scope.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict.
 */
public class DuplicateSiteException extends BusinessException {

    private static final String ERROR_CODE = "ERR-SITE-00409";

    public DuplicateSiteException(String message) {
        super(ERROR_CODE, message);
    }
}
