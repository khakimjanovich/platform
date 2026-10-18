package uz.heeypay.ecom.persistence;

import lombok.Getter;
import lombok.Setter;

/**
 * How one module connects: bound by each module to its own prefix, e.g. {@code ecom.db.partner},
 * with a {@code @ConfigurationProperties} bean method.
 */
@Getter
@Setter
public class ModuleDatabaseProperties {
  private String url;
  private String username;
  private String password;
}
