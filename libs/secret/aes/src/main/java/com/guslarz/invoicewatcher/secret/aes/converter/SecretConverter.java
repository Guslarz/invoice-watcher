package com.guslarz.invoicewatcher.secret.aes.converter;

import com.guslarz.invoicewatcher.secret.api.Secret;
import com.guslarz.invoicewatcher.secret.api.SecretFactory;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConfigurationPropertiesBinding
public class SecretConverter implements Converter<String, Secret> {

  // Resolved lazily: the factory needs the bound key, and binding needs this converter
  private final ObjectProvider<SecretFactory> secretFactory;

  @Override
  public Secret convert(@Nullable String source) {
    return Optional.ofNullable(source)
        .map(encrypted -> secretFactory.getObject().fromEncrypted(encrypted))
        .orElse(null);
  }
}
