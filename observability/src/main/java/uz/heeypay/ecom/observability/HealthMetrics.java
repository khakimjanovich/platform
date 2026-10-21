package uz.heeypay.ecom.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;

/**
 * {@code ecom.health.up{component}}: 1 while a health indicator says UP, 0 otherwise (also when it
 * throws), read each time metrics are exported. Registered once every bean exists, so every
 * indicator is known. This is how an alert sees a dependency go DOWN without anyone polling {@code
 * /actuator/health}.
 */
@RequiredArgsConstructor
class HealthMetrics implements SmartInitializingSingleton {

  private final ObjectProvider<HealthContributorRegistry> registry;
  private final ObjectProvider<MeterRegistry> meters;

  @Override
  public void afterSingletonsInstantiated() {
    var contributors = registry.getIfAvailable();
    var meters = this.meters.getIfAvailable();
    if (contributors == null || meters == null) return;
    contributors.stream()
        .filter(named -> named.contributor() instanceof HealthIndicator)
        .forEach(
            named -> {
              var indicator = (HealthIndicator) named.contributor();
              Gauge.builder(ObservabilityConstants.HEALTH_METRIC, () -> up(indicator))
                  .description(ObservabilityConstants.HEALTH_METRIC_DESCRIPTION)
                  .tag(ObservabilityConstants.COMPONENT_TAG, named.name())
                  .register(meters);
            });
  }

  private static double up(HealthIndicator indicator) {
    try {
      var health = indicator.health();
      return health != null && Status.UP.equals(health.getStatus()) ? 1 : 0;
    } catch (RuntimeException e) {
      return 0;
    }
  }
}
