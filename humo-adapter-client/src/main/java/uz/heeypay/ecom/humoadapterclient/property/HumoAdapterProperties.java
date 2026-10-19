package uz.heeypay.ecom.humoadapterclient.property;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where the Humo adapter is, and how long to wait for it. */
@ConfigurationProperties("humo.adapter.client")
public record HumoAdapterProperties(
    String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("40s") Duration readTimeout) {}
