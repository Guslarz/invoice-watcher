package com.guslarz.invoicewatcher.testing.testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.testcontainers.gcloud.DatastoreEmulatorContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Fresh unstarted fixtures; each test owns its container lifecycle. */
@UtilityClass
public class SharedTestcontainers {
  private static final Map<String, String> SETTINGS = load();

  public KafkaContainer kafka() {
    return new KafkaContainer(DockerImageName.parse(setting("kafka.image")));
  }

  public DatastoreEmulatorContainer datastore() {
    return new DatastoreEmulatorContainer(DockerImageName.parse(setting("datastore.image")))
        .withFlags(setting("datastore.flags"));
  }

  public String image(String kind) {
    return setting(kind + ".image");
  }

  private String setting(String key) {
    String value = SETTINGS.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing shared test container setting");
    }
    return value;
  }

  private Map<String, String> load() {
    try (InputStream input =
        SharedTestcontainers.class.getResourceAsStream("/testcontainers-images.properties")) {
      if (input == null) {
        throw new IllegalStateException("Missing shared test container resource");
      }
      Properties properties = new Properties();
      properties.load(input);
      return Map.copyOf(
          properties.stringPropertyNames().stream()
              .collect(Collectors.toMap(key -> key, properties::getProperty)));
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read shared test container resource");
    }
  }
}
