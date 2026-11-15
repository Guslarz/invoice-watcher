package com.guslarz.invoicewatcher.dao.userconfiguration.api.service;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import java.util.Optional;

/** Narrow, conditional updates that cannot restore a disconnected grant. */
public interface GmailGrantDao {

  Optional<GmailOauthCredentials> findGrant(String email, String grantId);

  boolean updateGrant(String email, String grantId, long revision, GmailOauthCredentials next);
}
