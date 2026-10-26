package uz.khakimjanovich.platform.persistence.b;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Module b's table. */
@Entity
public class Note {
  @Id Long id;
  String text;
}
