package uz.heeypay.ecom.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

/** A data source that counts the JDBC batches sent through it. */
final class CountingDataSource {

  final AtomicInteger batches = new AtomicInteger();
  final DataSource dataSource;

  CountingDataSource(DataSource target) {
    dataSource =
        proxy(DataSource.class, target, (method, result) -> wrapConnection(result, method));
  }

  private Object wrapConnection(Object result, String method) {
    return method.equals("getConnection")
        ? proxy(Connection.class, result, (inner, value) -> wrapStatement(value, inner))
        : result;
  }

  private Object wrapStatement(Object result, String method) {
    return method.equals("prepareStatement")
        ? proxy(
            PreparedStatement.class,
            result,
            (inner, value) -> {
              if (inner.equals("executeBatch")) batches.incrementAndGet();
              return value;
            })
        : result;
  }

  private interface After {
    Object apply(String method, Object result);
  }

  private static <T> T proxy(Class<T> type, Object target, After after) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (proxy, method, arguments) -> {
              try {
                return after.apply(method.getName(), method.invoke(target, arguments));
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            }));
  }
}
