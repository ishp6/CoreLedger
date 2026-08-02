package com.coreledger.exception;

public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String accountNumber) {
        super("Account already exists: " + accountNumber);
    }
}
