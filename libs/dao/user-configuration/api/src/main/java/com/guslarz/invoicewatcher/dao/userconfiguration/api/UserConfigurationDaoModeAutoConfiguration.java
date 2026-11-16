package com.guslarz.invoicewatcher.dao.userconfiguration.api;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Binds {@link UserConfigurationDaoProperties} for every implementation of the area. A missing or
 * unknown mode fails startup and names the property.
 */
@AutoConfiguration
@EnableConfigurationProperties(UserConfigurationDaoProperties.class)
public class UserConfigurationDaoModeAutoConfiguration {}
