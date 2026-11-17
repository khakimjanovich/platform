package uz.khakimjanovich.platform.observability.context;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import org.springframework.core.task.TaskDecorator;

public class ContextPropagatingTaskDecorator implements TaskDecorator {

  private final ContextSnapshotFactory snapshotFactory;

  public ContextPropagatingTaskDecorator() {
    this(defaultSnapshotFactory());
  }

  public ContextPropagatingTaskDecorator(ContextSnapshotFactory snapshotFactory) {
    this.snapshotFactory =
        Objects.requireNonNull(snapshotFactory, "snapshotFactory must not be null");
  }

  @Override
  public Runnable decorate(Runnable runnable) {
    return snapshotFactory.captureAll().wrap(runnable);
  }

  public static Runnable wrap(Runnable runnable) {
    ensureSlf4jAccessor(ContextRegistry.getInstance());
    return ContextSnapshotFactory.builder().build().captureAll().wrap(runnable);
  }

  public static <T> Callable<T> wrap(Callable<T> callable) {
    ensureSlf4jAccessor(ContextRegistry.getInstance());
    return ContextSnapshotFactory.builder().build().captureAll().wrap(callable);
  }

  public static Executor wrap(Executor executor) {
    ensureSlf4jAccessor(ContextRegistry.getInstance());
    return ContextSnapshotFactory.builder().build().captureAll().wrapExecutor(executor);
  }

  private static ContextSnapshotFactory defaultSnapshotFactory() {
    ensureSlf4jAccessor(ContextRegistry.getInstance());
    return ContextSnapshotFactory.builder().build();
  }

  private static void ensureSlf4jAccessor(ContextRegistry registry) {
    boolean hasSlf4j =
        registry.getThreadLocalAccessors().stream()
            .anyMatch(a -> Slf4jThreadLocalAccessor.KEY.equals(a.key()));
    if (!hasSlf4j) {
      registry.registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());
    }
  }
}
