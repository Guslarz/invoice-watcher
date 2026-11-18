package com.guslarz.invoicewatcher.dao.userconfiguration.jpa;

import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.FolderConfigEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.MailScanConfigEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.UserEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.mailcredentials.BasicAuthMailCredentialsEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.mailcredentials.GmailOauthCredentialsEntity;
import com.guslarz.invoicewatcher.dao.userconfiguration.jpa.entity.mailcredentials.MailCredentialsEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.DriverManager;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

/** Explicit initialization of disposable local H2; this is not a schema migration tool. */
@UtilityClass
public class LocalH2SchemaInitializer {

  // This initializer accepts only the loopback disposable H2 server, whose initial sa password is
  // empty.
  @SuppressFBWarnings(
      value = "DMI_EMPTY_DB_PASSWORD",
      justification = "Explicit initialization of loopback-only disposable H2")
  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !args[0].startsWith("jdbc:h2:tcp://localhost:9093/")) {
      throw new IllegalArgumentException("Only the loopback local H2 server is supported");
    }
    try (var connection = DriverManager.getConnection(args[0], "sa", "");
        var tables =
            connection.getMetaData().getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
      if (tables.next()) {
        throw new IllegalStateException("Refusing to initialize a database with existing tables");
      }
    }
    var registry =
        new StandardServiceRegistryBuilder()
            .applySettings(
                Map.of(
                    "jakarta.persistence.jdbc.url",
                    args[0],
                    "jakarta.persistence.jdbc.user",
                    "sa",
                    "jakarta.persistence.jdbc.password",
                    "",
                    "hibernate.hbm2ddl.auto",
                    "create-only",
                    "hibernate.dialect",
                    "org.hibernate.dialect.H2Dialect",
                    "hibernate.physical_naming_strategy",
                    "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                    "hibernate.implicit_naming_strategy",
                    "org.springframework.boot.hibernate.SpringImplicitNamingStrategy"))
            .build();
    try (var factory =
        new MetadataSources(registry)
            .addAnnotatedClass(UserEntity.class)
            .addAnnotatedClass(MailScanConfigEntity.class)
            .addAnnotatedClass(FolderConfigEntity.class)
            .addAnnotatedClass(MailCredentialsEntity.class)
            .addAnnotatedClass(BasicAuthMailCredentialsEntity.class)
            .addAnnotatedClass(GmailOauthCredentialsEntity.class)
            .buildMetadata()
            .buildSessionFactory()) {
      factory.getMetamodel();
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }
}
