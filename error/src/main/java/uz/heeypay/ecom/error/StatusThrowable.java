package uz.heeypay.ecom.error;

/**
 * An error a service can throw. Each service lists its own in an enum ({@code
 * <Service>ExceptionStatus}); the type is the stable code clients see, and its HTTP status, title
 * and messages are defined in {@code properties/<service>-exception.yaml}.
 */
public interface StatusThrowable {

  String getType();
}
