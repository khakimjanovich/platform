package uz.khakimjanovich.platform.observability.breaker;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uz.khakimjanovich.platform.breaker.Breaker;
import uz.khakimjanovich.platform.breaker.BreakerConstants;
import uz.khakimjanovich.platform.observability.ObservabilityAutoConfiguration;

class BreakerMetricsTest {

  @Test
  void bindsAllBreakerBeansToMeterRegistryOnStartup() {
    var breaker1 = new Breaker("payment-rail", 3, Duration.ofSeconds(10));
    var breaker2 = new Breaker("notification-service", 5, Duration.ofSeconds(30));
    var registry = new SimpleMeterRegistry();

    var factory =
        new StaticListableBeanFactory(
            Map.of("paymentBreaker", breaker1, "notificationBreaker", breaker2));
    ObjectProvider<Breaker> breakersProvider = factory.getBeanProvider(Breaker.class);
    ObjectProvider<MeterRegistry> metersProvider =
        new StaticListableBeanFactory(Map.of("meterRegistry", registry))
            .getBeanProvider(MeterRegistry.class);

    new BreakerMetrics(breakersProvider, metersProvider).afterSingletonsInstantiated();

    assertThat(
            registry
                .get(BreakerConstants.BREAKER_STATE_METRIC)
                .tag(BreakerConstants.NAME_TAG, "payment-rail")
                .gauge()
                .value())
        .isEqualTo(0.0);
    assertThat(
            registry
                .get(BreakerConstants.BREAKER_THRESHOLD_METRIC)
                .tag(BreakerConstants.NAME_TAG, "payment-rail")
                .gauge()
                .value())
        .isEqualTo(3.0);

    assertThat(
            registry
                .get(BreakerConstants.BREAKER_STATE_METRIC)
                .tag(BreakerConstants.NAME_TAG, "notification-service")
                .gauge()
                .value())
        .isEqualTo(0.0);
    assertThat(
            registry
                .get(BreakerConstants.BREAKER_THRESHOLD_METRIC)
                .tag(BreakerConstants.NAME_TAG, "notification-service")
                .gauge()
                .value())
        .isEqualTo(5.0);
  }

  @Test
  void autoConfigurationProvidesBreakerMetricsWhenClassesPresent() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withBean(
            "customBreaker", Breaker.class, () -> new Breaker("custom", 4, Duration.ofSeconds(5)))
        .run(
            context -> {
              assertThat(context).hasSingleBean(BreakerMetrics.class);
              var registry = context.getBean(MeterRegistry.class);
              assertThat(
                      registry
                          .get(BreakerConstants.BREAKER_STATE_METRIC)
                          .tag(BreakerConstants.NAME_TAG, "custom")
                          .gauge()
                          .value())
                  .isEqualTo(0.0);
            });
  }
}
