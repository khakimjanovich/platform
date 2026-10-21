package uz.heeypay.ecom.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import uz.heeypay.ecom.masking.Masking;

/**
 * The guard on every log line, wherever it goes: the readable console, ECS JSON and OTLP all print
 * the message masked (card numbers, {@code key=value} secrets). Logging is static, so the masking
 * in use is too: the defaults until the app's own names are bound.
 */
public final class LogMasking {

  /** Logback's names for the message in a pattern: %m, %msg, %message. */
  private static final List<String> MESSAGE_WORDS = List.of("m", "msg", "message");

  /** ECS members that carry text a program wrote. */
  private static final List<String> JSON_TEXT = List.of("message", "error.message");

  private static volatile Masking masking = Masking.defaults();

  private LogMasking() {}

  public static Masking masking() {
    return masking;
  }

  /** Masks with these rules from now on, and makes the readable console print masked messages. */
  static void install(LoggerContext context, Masking rules) {
    masking = rules;
    for (var appenders = context.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders();
        appenders.hasNext(); ) {
      var appender = appenders.next();
      if (appender instanceof OutputStreamAppender<?> stream
          && stream.getEncoder() instanceof LayoutWrappingEncoder<?> encoder
          && encoder.getLayout() instanceof PatternLayout layout) {
        MESSAGE_WORDS.forEach(word -> layout.getInstanceConverterMap().put(word, Message::new));
        layout.stop();
        layout.start();
      }
    }
  }

  /** The message as {@code %m} prints it, masked. */
  public static class Message extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
      return masking.text(super.convert(event));
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

  /** The same event with its message masked, for appenders that read it themselves (OTLP). */
  static ILoggingEvent masked(ILoggingEvent event) {
    return (ILoggingEvent)
        Proxy.newProxyInstance(
            ILoggingEvent.class.getClassLoader(),
            new Class<?>[] {ILoggingEvent.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getFormattedMessage", "getMessage" ->
                      masking.text(event.getFormattedMessage());
                  case "getArgumentArray" -> null;
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
