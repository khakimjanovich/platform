package uz.heeypay.ecom.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;

class HealthMetricsTest {

  @Test
  void eachIndicatorIsOneWhileUpAndZeroWhenDownOrBroken() {
    var down = new boolean[] {false};
    var registry = new DefaultHealthContributorRegistry();
    registry.registerContributor(
        "store", (HealthIndicator) () -> down[0] ? Health.down().build() : Health.up().build());
    registry.registerContributor(
        "broken",
        (HealthIndicator)
            () -> {
              throw new IllegalStateException("boom");
            });
    var meters = new SimpleMeterRegistry();
    new HealthMetrics(provider(registry), provider(meters)).afterSingletonsInstantiated();

    assertThat(meters.get("ecom.health.up").tag("component", "store").gauge().value()).isEqualTo(1);
    assertThat(meters.get("ecom.health.up").tag("component", "broken").gauge().value()).isZero();
    down[0] = true;
    assertThat(meters.get("ecom.health.up").tag("component", "store").gauge().value()).isZero();
  }

  private static <T> org.springframework.beans.factory.ObjectProvider<T> provider(T bean) {
    return new org.springframework.beans.factory.support.StaticListableBeanFactory(
            java.util.Map.of("bean", bean))
        .getBeanProvider((Class<T>) bean.getClass());
  }

  @Test
  void anAppWithTheActuatorGetsTheBinder() {
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                org.springframework.boot.health.autoconfigure.registry
                    .HealthContributorRegistryAutoConfiguration.class,
                ObservabilityAutoConfiguration.class))
        .withBean(io.micrometer.core.instrument.MeterRegistry.class, SimpleMeterRegistry::new)
        .run(context -> assertThat(context).hasSingleBean(HealthMetrics.class));
  }
}
