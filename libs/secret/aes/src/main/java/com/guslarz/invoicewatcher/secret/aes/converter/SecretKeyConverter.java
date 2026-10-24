package com.guslarz.invoicewatcher.secret.aes.converter;

import com.guslarz.invoicewatcher.util.aes.AesUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Optional;

@Component
@ConfigurationPropertiesBinding
public class SecretKeyConverter implements Converter<String, SecretKey> {

  @Override
  public SecretKey convert(@Nullable String source) {
    return Optional.ofNullable(source)
        .map(AesUtil::decodeSecretKey)
        .orElse(null);
  }
}
