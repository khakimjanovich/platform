package uz.khakimjanovich.platform.observability.breaker;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import uz.khakimjanovich.platform.breaker.Breaker;
import uz.khakimjanovich.platform.breaker.BreakerMeterBinder;

@RequiredArgsConstructor
public class BreakerMetrics implements SmartInitializingSingleton {

  private final ObjectProvider<Breaker> breakers;
  private final ObjectProvider<MeterRegistry> meters;

  @Override
  public void afterSingletonsInstantiated() {
    var registry = meters.getIfAvailable();
    if (registry == null) {
      return;
    }
    breakers.orderedStream().forEach(breaker -> new BreakerMeterBinder(breaker).bindTo(registry));
  }
}
