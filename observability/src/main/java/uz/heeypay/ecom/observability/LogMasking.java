package uz.heeypay.ecom.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import uz.heeypay.ecom.masking.Masking;

/**
 * The guard on every log line, wherever it goes: the readable console, ECS JSON and OTLP all print
 * the message and its stack trace masked (card numbers, {@code key=value} secrets). Logging is
 * static, so the masking in use is too: the defaults until the app's own names are bound, and
 * nothing logged before the context starts (the banner, Vault's bootstrap) is masked.
 */
public final class LogMasking {

  /** Logback's names for the message in a pattern: %m, %msg, %message. */
  private static final List<String> MESSAGE_WORDS = List.of("m", "msg", "message");

  /** Logback's and Boot's names for the stack trace in a pattern (Boot's default is %wEx). */
  private static final List<String> THROWABLE_WORDS =
      List.of("ex", "exception", "throwable", "xEx", "xException", "xThrowable", "wex", "wEx");

  /** ECS members that carry text a program wrote. */
  private static final List<String> JSON_TEXT =
      List.of("message", "error.message", "error.stack_trace");

  private static volatile Masking masking = Masking.defaults();

  private LogMasking() {}

  public static Masking masking() {
    return masking;
  }

  /**
   * Masks with these rules from now on, and makes the readable console print masked messages and
   * stack traces.
   */
  static void install(LoggerContext context, Masking rules) {
    masking = rules;
    for (var appenders = context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
        appenders.hasNext(); ) {
      var appender = appenders.next();
      if (appender instanceof OutputStreamAppender<?> stream
          && stream.getEncoder() instanceof LayoutWrappingEncoder<?> encoder
          && encoder.getLayout() instanceof PatternLayout layout) {
        MESSAGE_WORDS.forEach(word -> layout.getInstanceConverterMap().put(word, Message::new));
        THROWABLE_WORDS.forEach(word -> layout.getInstanceConverterMap().put(word, Trace::new));
        layout.stop();
        layout.start();
      }
    }
  }

  /** The message as {@code %m} prints it, masked. */
  public static class Message extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
      var text = masking.text(super.convert(event));
      return text == null ? "" : text;
    }
  }

  /** The stack trace as {@code %wEx} prints it, masked. */
  public static class Trace extends ExtendedWhitespaceThrowableProxyConverter {
    @Override
    protected String throwableProxyToString(IThrowableProxy proxy) {
      var text = masking.text(super.throwableProxyToString(proxy));
      return text == null ? "" : text;
    }
  }

  /** ECS JSON: {@code logging.structured.json.customizer} names this class (the defaults do). */
  public static class Json implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
      for (var path : JSON_TEXT) {
        members.applyingValueProcessor(
            JsonWriter.ValueProcessor.of(String.class, text -> masking.text(text))
                .whenHasPath(path));
      }
    }
  }

  /**
   * The same event with its message masked, for appenders that read it themselves (OTLP). Its stack
   * trace travels masked inside the message, and the event has no throwable of its own.
   */
  static ILoggingEvent masked(ILoggingEvent event) {
    var trace = event.getThrowableProxy();
    // ponytail: exception.type/stacktrace attributes dropped for OTLP; add a masked IThrowableProxy
    // if dashboards need exception.type.
    var message =
        trace == null
            ? event.getFormattedMessage()
            : event.getFormattedMessage()
                + System.lineSeparator()
                + ThrowableProxyUtil.asString(trace);
    return (ILoggingEvent)
        Proxy.newProxyInstance(
            ILoggingEvent.class.getClassLoader(),
            new Class<?>[] {ILoggingEvent.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getFormattedMessage", "getMessage" -> masking.text(message);
                  case "getArgumentArray", "getThrowableProxy" -> null;
                  default -> {
                    try {
                      yield method.invoke(event, arguments);
                    } catch (InvocationTargetException e) {
                      throw e.getCause();
                    }
                  }
                });
  }
}
