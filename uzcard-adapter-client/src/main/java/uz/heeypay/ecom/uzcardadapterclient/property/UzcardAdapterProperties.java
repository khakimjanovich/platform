package uz.heeypay.ecom.uzcardadapterclient.property;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where the Uzcard adapter is, and how long to wait for it. */
@ConfigurationProperties("uzcard.adapter.client")
public record UzcardAdapterProperties(
    String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("40s") Duration readTimeout) {}
