package uz.khakimjanovich.platform.valkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ResourceBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Counters and limits against a real Valkey, signed in as an ACL user like heeyid-infra's: it
 * reaches only keys {@code customer-gw:*} and only the commands it sends. The user it-admin reads
 * and clears for the tests.
 */
@Testcontainers
class ValkeyKeysIT {

  @Container
  static final GenericContainer<?> VALKEY =
      new GenericContainer<>(ResourceBundle.getBundle("images").getString("valkey"))
          .withCommand(
              "valkey-server",
              "--user",
              "default",
              "off",
              "--user",
              "customer-gw",
              "on",
              ">valkey-it",
              "resetkeys",
              "~customer-gw:*",
              "resetchannels",
              "-@all",
              "+get",
              "+set",
              "+del",
              "+incr",
              "+expire",
              "+ttl",
              "+ping",
              "+info",
              "+hello",
              "+client|setinfo",
              "--user",
              "it-admin",
              "on",
              ">admin-it",
              "~*",
              "+@all")
          .withExposedPorts(6379);

  private StringRedisTemplate admin;

  private ApplicationContextRunner app(String service) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(DataRedisAutoConfiguration.class, ValkeyAutoConfiguration.class))
        .withPropertyValues(
            "spring.data.redis.host=" + VALKEY.getHost(),
            "spring.data.redis.port=" + VALKEY.getMappedPort(6379),
            "spring.data.redis.username=customer-gw",
            "spring.data.redis.password=valkey-it",
            "platform.valkey.service=" + service);
  }

  @BeforeEach
  void emptyValkey() {
    if (admin == null) {
      var config = new RedisStandaloneConfiguration(VALKEY.getHost(), VALKEY.getMappedPort(6379));
      config.setUsername("it-admin");
      config.setPassword("admin-it");
      var factory = new LettuceConnectionFactory(config);
      factory.afterPropertiesSet();
      factory.start();
      admin = new StringRedisTemplate(factory);
    }
    admin.delete(admin.keys("*"));
  }

  @Test
  void aCounterCountsUnderTheServicePrefixAndExpiresWithItsWindow() {
    app("customer-gw")
        .run(
            context -> {
              var keys = context.getBean(ValkeyKeys.class);
              assertThat(keys.increment("sends", "n1", Duration.ofMinutes(10))).isEqualTo(1);
              assertThat(keys.increment("sends", "n1", Duration.ofMinutes(10))).isEqualTo(2);
              assertThat(keys.count("sends", "n1")).isEqualTo(2);
              assertThat(keys.count("sends", "n2")).isZero();
              assertThat(admin.keys("*")).containsExactly("customer-gw:sends:n1");
              assertThat(keys.expiresIn("sends", "n1"))
                  .hasValueSatisfying(
                      left ->
                          assertThat(left)
                              .isBetween(Duration.ofMinutes(9), Duration.ofMinutes(10)));
            });
  }

  @Test
  void theWindowStartsWithTheFirstCountAndALaterCountDoesNotExtendIt() throws Exception {
    app("customer-gw")
        .run(
            context -> {
              var keys = context.getBean(ValkeyKeys.class);
              keys.increment("sends", "n1", Duration.ofSeconds(1));
              keys.increment("sends", "n1", Duration.ofHours(1));
              Thread.sleep(1500);
              assertThat(keys.count("sends", "n1")).isZero();
              assertThat(keys.increment("sends", "n1", Duration.ofSeconds(1))).isEqualTo(1);
            });
  }

  @Test
  void aCounterThatLostItsExpiryGetsItOnTheNextCount() {
    admin.opsForValue().set("customer-gw:sends:n1", "3");
    app("customer-gw")
        .run(
            context -> {
              var keys = context.getBean(ValkeyKeys.class);
              assertThat(keys.expiresIn("sends", "n1")).isEmpty();
              assertThat(keys.increment("sends", "n1", Duration.ofMinutes(5))).isEqualTo(4);
              assertThat(keys.expiresIn("sends", "n1")).isPresent();
            });
  }

  @Test
  void aLimitRefusesPastItsMaximumWithTheTimeLeftAndAResetClearsIt() {
    app("customer-gw")
        .run(
            context -> {
              var keys = context.getBean(ValkeyKeys.class);
              var window = Duration.ofMinutes(15);
              assertThat(keys.hit("checks", "c1", 2, window)).isEmpty();
              assertThat(keys.hit("checks", "c1", 2, window)).isEmpty();
              assertThat(keys.hit("checks", "c1", 2, window))
                  .hasValueSatisfying(
                      wait -> assertThat(wait).isBetween(Duration.ofMinutes(14), window));
              keys.reset("checks", "c1");
              assertThat(keys.count("checks", "c1")).isZero();
              assertThat(keys.hit("checks", "c1", 2, window)).isEmpty();
            });
  }

  @Test
  void theAclRefusesAServiceThatIsNotTheUsersOwn() {
    app("admin-gw")
        .run(
            context ->
                assertThatThrownBy(
                        () ->
                            context
                                .getBean(ValkeyKeys.class)
                                .increment("sends", "n1", Duration.ofMinutes(1)))
                    .isInstanceOf(DataAccessException.class)
                    .rootCause()
                    .hasMessageContaining("NOPERM"));
  }

  @Test
  void aValkeyThatCannotBeReachedThrows() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(DataRedisAutoConfiguration.class, ValkeyAutoConfiguration.class))
        .withPropertyValues(
            "spring.data.redis.host=" + VALKEY.getHost(),
            "spring.data.redis.port=1",
            "spring.data.redis.timeout=1s",
            "spring.data.redis.connect-timeout=1s",
            "platform.valkey.service=customer-gw")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(ValkeyKeys.class).count("sends", "n1"))
                    .isInstanceOf(DataAccessException.class));
  }
}
