package com.cfsl.easymrcp.sip;

import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.UserCredentials;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.sip.ClientTransaction;

@Data
@AllArgsConstructor
public class AccountManagerImpl implements AccountManager {
    private String userName;
    private String password;

    public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm) {
       return new UserCredentialsImpl(userName,"172.16.2.119",password);
    }

}
