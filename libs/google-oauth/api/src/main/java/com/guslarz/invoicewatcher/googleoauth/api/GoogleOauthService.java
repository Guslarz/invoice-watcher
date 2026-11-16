package com.guslarz.invoicewatcher.googleoauth.api;

import com.guslarz.invoicewatcher.mailconnector.api.OauthAccessTokenProvider;
import com.guslarz.invoicewatcher.secret.api.Secret;

public interface GoogleOauthService extends OauthAccessTokenProvider {
  boolean revoke(Secret refreshToken);
}
