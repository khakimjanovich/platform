package uz.heeypay.ecom.persistence.c;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Module c's entity, ahead of its migrations: it has a column the table doesn't. */
@Entity
public class Note {
  @Id Long id;
  String text;
  String author;
}
