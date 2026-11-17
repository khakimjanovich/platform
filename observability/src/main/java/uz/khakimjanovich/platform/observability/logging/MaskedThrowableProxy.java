package uz.khakimjanovich.platform.observability.logging;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import uz.khakimjanovich.platform.masking.Masking;

public final class MaskedThrowableProxy extends ThrowableProxy {

  private static final long serialVersionUID = 1L;

  private final Throwable sanitizedThrowable;
  private final String originalClassName;
  private final @Nullable String maskedMessage;
  private final @Nullable IThrowableProxy maskedCause;
  private final IThrowableProxy[] maskedSuppressed;

  private MaskedThrowableProxy(
      Throwable sanitizedThrowable,
      String originalClassName,
      @Nullable String maskedMessage,
      @Nullable IThrowableProxy maskedCause,
      IThrowableProxy[] maskedSuppressed) {
    super(sanitizedThrowable);
    this.sanitizedThrowable = sanitizedThrowable;
    this.originalClassName = originalClassName;
    this.maskedMessage = maskedMessage;
    this.maskedCause = maskedCause;
    this.maskedSuppressed = maskedSuppressed;
  }

  public static @Nullable MaskedThrowableProxy of(
      @Nullable IThrowableProxy originalProxy, Masking masking) {
    if (originalProxy == null) {
      return null;
    }
    return of(originalProxy, masking, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static @Nullable MaskedThrowableProxy of(
      @Nullable IThrowableProxy proxy, Masking masking, Set<IThrowableProxy> seenProxies) {
    if (proxy == null || !seenProxies.add(proxy)) {
      return null;
    }

    var originalClassName = proxy.getClassName();
    var rawMessage = proxy.getMessage();
    var maskedMessage = rawMessage == null ? null : masking.text(rawMessage);

    var causeProxy = proxy.getCause();
    var maskedCause =
        causeProxy != null && !seenProxies.contains(causeProxy)
            ? of(causeProxy, masking, seenProxies)
            : null;

    var rawSuppressed = proxy.getSuppressed();
    IThrowableProxy[] maskedSuppressed;
    if (rawSuppressed == null || rawSuppressed.length == 0) {
      maskedSuppressed = new IThrowableProxy[0];
    } else {
      var list = new ArrayList<IThrowableProxy>(rawSuppressed.length);
      for (var s : rawSuppressed) {
        if (s != null && !seenProxies.contains(s)) {
          var masked = of(s, masking, seenProxies);
          if (masked != null) {
            list.add(masked);
          }
        }
      }
      maskedSuppressed = list.toArray(IThrowableProxy[]::new);
    }

    var causeThrowable = maskedCause != null ? maskedCause.getThrowable() : null;

    Throwable liveThrowable = null;
    if (proxy instanceof ThrowableProxy tp) {
      liveThrowable = tp.getThrowable();
    }

    Throwable sanitizedThrowable;
    if (liveThrowable != null) {
      sanitizedThrowable =
          createSanitizedInstance(liveThrowable.getClass(), maskedMessage, causeThrowable);
      sanitizedThrowable.setStackTrace(liveThrowable.getStackTrace());
    } else {
      var fallback = new MaskedException(originalClassName, maskedMessage, causeThrowable);
      var stepArray = proxy.getStackTraceElementProxyArray();
      if (stepArray != null) {
        var elements = new StackTraceElement[stepArray.length];
        for (var i = 0; i < stepArray.length; i++) {
          elements[i] = stepArray[i].getStackTraceElement();
        }
        fallback.setStackTrace(elements);
      }
      sanitizedThrowable = fallback;
    }

    for (var suppressed : maskedSuppressed) {
      if (suppressed instanceof ThrowableProxy tp && tp.getThrowable() != null) {
        sanitizedThrowable.addSuppressed(tp.getThrowable());
      }
    }

    return new MaskedThrowableProxy(
        sanitizedThrowable, originalClassName, maskedMessage, maskedCause, maskedSuppressed);
  }

  // Candidate exception constructors are attempted in order; missing signatures are expected.
  @SuppressWarnings("EmptyCatch")
  private static Throwable createSanitizedInstance(
      Class<? extends Throwable> clazz, @Nullable String maskedMessage, @Nullable Throwable cause) {
    try {
      var ctor = clazz.getConstructor(String.class, Throwable.class);
      return ctor.newInstance(maskedMessage, cause);
    } catch (ReflectiveOperationException expected) {
      // try next candidate constructor
    }

    try {
      var ctor = clazz.getConstructor(String.class);
      var instance = ctor.newInstance(maskedMessage);
      if (cause != null) {
        try {
          instance.initCause(cause);
        } catch (IllegalStateException expected) {
          // cause already initialized
        }
      }
      return instance;
    } catch (ReflectiveOperationException expected) {
      // try next candidate constructor
    }

    try {
      var ctor = clazz.getDeclaredConstructor(String.class, Throwable.class);
      ctor.setAccessible(true);
      return ctor.newInstance(maskedMessage, cause);
    } catch (Throwable expected) {
      // try next candidate constructor
    }

    try {
      var ctor = clazz.getDeclaredConstructor(String.class);
      ctor.setAccessible(true);
      var instance = ctor.newInstance(maskedMessage);
      if (cause != null) {
        try {
          instance.initCause(cause);
        } catch (IllegalStateException expected) {
          // cause already initialized
        }
      }
      return instance;
    } catch (Throwable expected) {
      // try next candidate constructor
    }

    try {
      var ctor = clazz.getConstructor(Throwable.class);
      return ctor.newInstance(cause);
    } catch (ReflectiveOperationException expected) {
      // try next candidate constructor
    }

    try {
      var ctor = clazz.getConstructor();
      var instance = ctor.newInstance();
      if (cause != null) {
        try {
          instance.initCause(cause);
        } catch (IllegalStateException expected) {
          // cause already initialized
        }
      }
      return instance;
    } catch (ReflectiveOperationException expected) {
      // fall through to synthetic fallback
    }

    return new MaskedException(clazz.getName(), maskedMessage, cause);
  }

  @Override
  public String getClassName() {
    return originalClassName;
  }

  @Override
  public @Nullable String getMessage() {
    return maskedMessage;
  }

  @Override
  public Throwable getThrowable() {
    return sanitizedThrowable;
  }

  @Override
  public @Nullable IThrowableProxy getCause() {
    return maskedCause;
  }

  @Override
  public IThrowableProxy[] getSuppressed() {
    return maskedSuppressed;
  }

  // Synthetic exception renders the original class name to preserve OpenTelemetry type semantics.
  @SuppressWarnings("OverrideThrowableToString")
  public static class MaskedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String originalClassName;

    public MaskedException(
        String originalClassName, @Nullable String message, @Nullable Throwable cause) {
      super(message, cause);
      this.originalClassName = originalClassName;
    }

    public String originalClassName() {
      return originalClassName;
    }

    @Override
    public String toString() {
      var message = getLocalizedMessage();
      return message != null ? (originalClassName + ": " + message) : originalClassName;
    }
  }
}
