package com.bsr.identity.api;

public interface AccountAuthentication {

    AccountCredentials loadByLoginName(String loginName);

    ActorContext loadActor(long accountId);
}
