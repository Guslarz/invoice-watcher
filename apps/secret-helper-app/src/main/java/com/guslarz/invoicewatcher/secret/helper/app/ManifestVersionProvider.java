package com.guslarz.invoicewatcher.secret.helper.app;

import java.util.Optional;
import picocli.CommandLine.IVersionProvider;

/**
 * Reads the version from the jar manifest; prints {@code unknown} when run from classes.
 */
public class ManifestVersionProvider implements IVersionProvider {

  @Override
  public String[] getVersion() {
    return new String[] {
        Optional.ofNullable(getClass().getPackage().getImplementationVersion())
            .orElse("unknown")
    };
  }
}
