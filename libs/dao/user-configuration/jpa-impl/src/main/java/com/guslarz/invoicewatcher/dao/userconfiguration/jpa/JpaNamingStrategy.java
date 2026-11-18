package com.guslarz.invoicewatcher.dao.userconfiguration.jpa;

import java.io.Serial;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitJoinTableNameSource;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;

/**
 * Preserves the existing owning-table/association join-table names without Boot auto-configuration.
 */
public class JpaNamingStrategy extends ImplicitNamingStrategyJpaCompliantImpl {
  @Serial private static final long serialVersionUID = 1L;

  @Override
  public Identifier determineJoinTableName(ImplicitJoinTableNameSource source) {
    String table = source.getOwningPhysicalTableName();
    String association = source.getAssociationOwningAttributePath().getProperty();
    return toIdentifier(table + "_" + association, source.getBuildingContext());
  }
}
