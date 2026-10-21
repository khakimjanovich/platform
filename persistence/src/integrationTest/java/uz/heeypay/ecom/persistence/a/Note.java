package uz.heeypay.ecom.persistence.a;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Module a's table. */
@Entity
public class Note {
  @Id Long id;
  String text;
}
