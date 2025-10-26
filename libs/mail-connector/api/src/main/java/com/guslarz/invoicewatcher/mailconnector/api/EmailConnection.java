package com.guslarz.invoicewatcher.mailconnector.api;

import java.util.List;

public interface EmailConnection {

  List<EmailMessage> getEmailMessages(EmailQuery query);
}
