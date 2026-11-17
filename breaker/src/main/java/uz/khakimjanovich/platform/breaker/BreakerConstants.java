package uz.khakimjanovich.platform.breaker;

public final class BreakerConstants {

  private BreakerConstants() {}

  public static final String DEFAULT_BREAKER_NAME = "default";

  public static final String BREAKER_STATE_METRIC = "platform.circuit-breaker.state";
  public static final String BREAKER_FAILURES_METRIC =
      "platform.circuit-breaker.consecutive-failures";
  public static final String BREAKER_THRESHOLD_METRIC =
      "platform.circuit-breaker.failure-threshold";
  public static final String BREAKER_CALLS_METRIC = "platform.circuit-breaker.calls";

  public static final String NAME_TAG = "name";
  public static final String RESULT_TAG = "result";

  public static final String RESULT_SUCCESS = "success";
  public static final String RESULT_FAILURE = "failure";
  public static final String RESULT_REJECTED = "rejected";
}
