package uz.heeypay.ecom.persistence.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;

/** Module a's table. */
@Entity
public class Note {
  @Id public Long id;
  public String text;
  public OffsetDateTime writtenAt;
}
