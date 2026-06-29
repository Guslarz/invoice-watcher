package com.guslarz.invoicewatcher.mailproducer.scheduler.impl;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan
@EnableScheduling
@AutoConfiguration
@EnableConfigurationProperties(MailProducerSchedulerProperties.class)
public class MailProducerSchedulerAutoConfiguration {
}
