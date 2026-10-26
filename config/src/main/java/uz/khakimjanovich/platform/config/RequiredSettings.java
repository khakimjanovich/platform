package uz.khakimjanovich.platform.config;

import java.util.ArrayList;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ApplicationListener;

/**
 * No setting has a default: a {@code ${VAR}} in any configuration file that nothing resolves stops
 * startup, naming every missing one. Spring's binder would otherwise bind the literal text. Runs
 * once the context is prepared, so values a test registers (DynamicPropertySource) count.
 */
public class RequiredSettings implements ApplicationListener<ApplicationPreparedEvent> {

  @Override
  public void onApplicationEvent(ApplicationPreparedEvent event) {
    var environment = event.getApplicationContext().getEnvironment();
    var missing = new ArrayList<String>();
    for (var source : environment.getPropertySources()) {
      if (!(source instanceof OriginTrackedMapPropertySource file)) continue;
      for (var name : file.getPropertyNames()) {
        try {
          environment.getProperty(name);
        } catch (IllegalArgumentException e) {
          missing.add(name + ": " + e.getMessage());
        }
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Required settings are missing; set them as environment variables:\n  "
              + String.join("\n  ", missing));
    }
  }
}
