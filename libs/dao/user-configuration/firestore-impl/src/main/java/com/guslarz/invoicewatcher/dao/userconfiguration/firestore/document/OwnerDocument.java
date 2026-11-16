package com.guslarz.invoicewatcher.dao.userconfiguration.firestore.document;

import com.google.cloud.spring.data.datastore.core.mapping.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "user-configuration-owners")
public class OwnerDocument {
  @Id private String id;
  private String email;
}
