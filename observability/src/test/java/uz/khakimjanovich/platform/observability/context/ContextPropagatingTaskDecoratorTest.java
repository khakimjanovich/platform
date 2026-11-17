package uz.khakimjanovich.platform.observability.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;
import uz.khakimjanovich.platform.observability.ObservabilityAutoConfiguration;

class ContextPropagatingTaskDecoratorTest {

  @AfterEach
  void cleanMdc() {
    MDC.clear();
  }

  @Test
  void propagatesMdcAcrossThreadBoundariesAndCleansUpAfterwards()
      throws ExecutionException, InterruptedException, TimeoutException {
    var decorator = new ContextPropagatingTaskDecorator();
    var executor = Executors.newSingleThreadExecutor();

    try {
      MDC.put("traceId", "test-trace-12345");
      MDC.put("userId", "usr-987");

      var observedMdcInsideTask = new AtomicReference<String>();
      var observedUserInsideTask = new AtomicReference<String>();

      Runnable task =
          () -> {
            observedMdcInsideTask.set(MDC.get("traceId"));
            observedUserInsideTask.set(MDC.get("userId"));
          };

      Runnable decorated = decorator.decorate(task);

      // Execute on worker thread
      CompletableFuture.runAsync(decorated, executor).get(5, TimeUnit.SECONDS);

      assertThat(observedMdcInsideTask.get()).isEqualTo("test-trace-12345");
      assertThat(observedUserInsideTask.get()).isEqualTo("usr-987");

      // Verify worker thread has clean MDC after completion (no thread-local leak)
      var leakCheck =
          CompletableFuture.supplyAsync(
                  () -> MDC.get("traceId") == null && MDC.get("userId") == null, executor)
              .get(5, TimeUnit.SECONDS);
      assertThat(leakCheck).isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void wrapCallableAndExecutorPropagatesContext()
      throws ExecutionException, InterruptedException, TimeoutException {
    var executor = Executors.newSingleThreadExecutor();
    try {
      MDC.put("requestId", "req-xyz");

      // Wrap callable
      var callable = ContextPropagatingTaskDecorator.wrap(() -> MDC.get("requestId"));
      var future = executor.submit(callable);
      assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("req-xyz");

      // Wrap executor
      var wrappedExecutor = ContextPropagatingTaskDecorator.wrap(executor);
      var result = new CompletableFuture<String>();
      wrappedExecutor.execute(() -> result.complete(MDC.get("requestId")));
      assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("req-xyz");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void autoConfigurationProvidesTaskDecoratorBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(TaskDecorator.class);
              assertThat(context.getBean(TaskDecorator.class))
                  .isInstanceOf(ContextPropagatingTaskDecorator.class);
            });
  }
}
