package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@AutoConfiguration
@Import(MailProducerScheduler.class)
@EnableConfigurationProperties(MailProducerSchedulerProperties.class)
public class MailProducerSchedulerAutoConfiguration {
}
