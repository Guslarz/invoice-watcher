package com.guslarz.invoicewatcher.mailconnector.api;

import lombok.Builder;

@Builder
public record EmailConnectionProperties(
    String host,
    int port,
    String username,
    String password
) {
}
