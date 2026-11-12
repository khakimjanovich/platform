package uz.khakimjanovich.platform.persistence.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;

@Entity
public class Note {
  @Id public Long id;
  public String text;
  public OffsetDateTime writtenAt;
}
