package com.guslarz.invoicewatcher.mailproducer.task;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(MailProducerTaskImpl.class)
public class MailProducerTaskAutoConfiguration {
}
