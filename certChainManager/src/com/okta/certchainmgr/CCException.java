package com.okta.certchainmgr;

public class CCException extends RuntimeException {
    public CCException(String s, Exception e) {
        super(s, e);
    }
}
