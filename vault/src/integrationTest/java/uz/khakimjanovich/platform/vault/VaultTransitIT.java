package uz.khakimjanovich.platform.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;
import uz.khakimjanovich.platform.error.GeneralException;

@Testcontainers
class VaultTransitIT {

  private static final String DATA = "data-key";
  private static final String INDEX = "index-key";
  private static final String ROW_A = "item.secret:7f1c";
  private static final String ROW_B = "item.secret:9a2e";

  @Container
  static final VaultContainer<?> VAULT =
      new VaultContainer<>(ResourceBundle.getBundle("images").getString("vault"))
          .withVaultToken("test")
          .withInitCommand(
              "secrets enable transit",
              "write -f transit/keys/" + DATA + " derived=true",
              "write -f transit/keys/" + INDEX);

  private static ApplicationContextRunner app(String token, String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.cloud.vault.config.VaultAutoConfiguration.class,
                VaultAutoConfiguration.class))
        .withPropertyValues(
            "spring.cloud.vault.uri=" + VAULT.getHttpHostAddress(),
            "spring.cloud.vault.authentication=TOKEN",
            "spring.cloud.vault.token=" + token)
        .withPropertyValues(properties);
  }

  private static void fails(Throwable e, VaultExceptionStatus status) {
    assertThat(e)
        .isInstanceOfSatisfying(
            GeneralException.class, g -> assertThat(g.getStatus()).isEqualTo(status));
  }

  private static ApplicationContextRunner app() {
    return app("test", "platform.vault.transit.mount=transit");
  }

  @Test
  void aSealedValueOpensOnlyWithItsOwnContext() {
    app()
        .run(
            context -> {
              var transit = context.getBean(VaultTransit.class);
              var sealed = transit.encrypt(DATA, ROW_A, "secret value");
              assertThat(sealed).startsWith("vault:v1:").doesNotContain("secret value");
              assertThat(transit.decrypt(DATA, ROW_A, sealed)).isEqualTo("secret value");
              assertThatThrownBy(() -> transit.decrypt(DATA, ROW_B, sealed))
                  .satisfies(e -> fails(e, VaultExceptionStatus.VAULT_TRANSIT_REFUSED));
              var tampered = sealed.substring(0, sealed.length() - 4) + "AAA=";
              assertThatThrownBy(() -> transit.decrypt(DATA, ROW_A, tampered))
                  .satisfies(e -> fails(e, VaultExceptionStatus.VAULT_TRANSIT_REFUSED));
            });
  }

  @Test
  void afterARotationRewrapMovesAValueToTheNewestVersionAndItStillOpens() throws Exception {
    app()
        .run(
            context -> {
              var transit = context.getBean(VaultTransit.class);
              var sealed = transit.encrypt(DATA, ROW_A, "rotated value");
              var rotated =
                  VAULT.execInContainer("vault", "write", "-f", "transit/keys/" + DATA + "/rotate");
              assertThat(rotated.getExitCode()).as(rotated.getStderr()).isZero();
              var latest = transit.latestPrefix(DATA);
              assertThat(sealed).doesNotStartWith(latest);
              var moved = transit.rewrap(DATA, ROW_A, sealed);
              assertThat(moved).startsWith(latest);
              assertThat(transit.decrypt(DATA, ROW_A, moved)).isEqualTo("rotated value");
            });
  }

  @Test
  void theSameValueGivesTheSameHmacAtAPinnedVersion() {
    app()
        .run(
            context -> {
              var transit = context.getBean(VaultTransit.class);
              var first = transit.hmac(INDEX, 1, "31234567890123");
              assertThat(first).startsWith("vault:v1:");
              assertThat(transit.hmac(INDEX, 1, "31234567890123")).isEqualTo(first);
              assertThat(transit.hmac(INDEX, 1, "31234567890124")).isNotEqualTo(first);
            });
  }

  @Test
  void aTokenVaultRefusesIsUnavailable() {
    app("wrong-token", "platform.vault.transit.mount=transit")
        .run(
            context ->
                assertThatThrownBy(
                        () -> context.getBean(VaultTransit.class).encrypt(DATA, ROW_A, "x"))
                    .satisfies(e -> fails(e, VaultExceptionStatus.VAULT_TRANSIT_UNAVAILABLE)));
  }

  @Test
  void aKeyThatDoesNotExistIsRefused() {
    app()
        .run(
            context -> {
              var transit = context.getBean(VaultTransit.class);
              assertThatThrownBy(() -> transit.latestPrefix("no-such-key"))
                  .satisfies(e -> fails(e, VaultExceptionStatus.VAULT_TRANSIT_REFUSED));
              assertThatThrownBy(() -> transit.decrypt("no-such-key", ROW_A, "vault:v1:AAAA"))
                  .satisfies(e -> fails(e, VaultExceptionStatus.VAULT_TRANSIT_REFUSED));
            });
  }

  @Test
  void aBlankMountStopsStartup() {
    app("test", "platform.vault.transit.mount=")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("platform.vault.transit")
                    .hasMessageContaining("mount"));
  }

  @Test
  void withoutAMountThereIsNoTransit() {
    app("test").run(context -> assertThat(context).doesNotHaveBean(VaultTransit.class));
  }
}
