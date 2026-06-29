package com.guslarz.invoicewatcher.mailconnector.api;

import java.util.List;

public interface MailConnection {

  List<MailMessage> getEmailMessages(MailQuery query);
}
