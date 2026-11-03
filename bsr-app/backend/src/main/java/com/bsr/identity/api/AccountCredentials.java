package com.bsr.identity.api;

public record AccountCredentials(long accountId, String loginName, String passwordHash, boolean enabled) {
}
