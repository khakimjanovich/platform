package uz.heeypay.ecom.security;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in person's name from the access token: an admin's username or a partner's email. */
@UtilityClass
public class CurrentUser {

  public static String name() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }
}
