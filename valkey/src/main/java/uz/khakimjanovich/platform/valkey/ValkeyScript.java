package uz.khakimjanovich.platform.valkey;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.script.RedisScript;

public record ValkeyScript<T>(RedisScript<T> target) {

  public static <T> ValkeyScript<T> of(Resource resource, Class<T> resultType) {
    return new ValkeyScript<>(RedisScript.of(resource, resultType));
  }

  public static <T> ValkeyScript<T> of(String script, Class<T> resultType) {
    return new ValkeyScript<>(RedisScript.of(script, resultType));
  }

  public static <T> ValkeyScript<T> ofClassPath(String path, Class<T> resultType) {
    return of(new ClassPathResource(path), resultType);
  }
}
