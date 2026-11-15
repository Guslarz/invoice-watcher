package com.guslarz.invoicewatcher.mailconnector.api;

import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.GmailOauthCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;

public interface OauthAccessTokenProvider {
  Secret accessToken(GmailOauthCredentials credentials);
}
