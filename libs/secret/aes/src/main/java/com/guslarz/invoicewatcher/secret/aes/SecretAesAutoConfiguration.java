package com.guslarz.invoicewatcher.secret.aes;

import com.guslarz.invoicewatcher.secret.aes.converter.SecretConverter;
import com.guslarz.invoicewatcher.secret.aes.converter.SecretKeyConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({SecretFactoryAesImpl.class, SecretConverter.class, SecretKeyConverter.class})
@EnableConfigurationProperties(SecretAesProperties.class)
public class SecretAesAutoConfiguration {
}
