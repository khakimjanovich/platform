package uz.heeypay.ecom.fileserviceclient.property;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where the file service is, and how long to wait for it. */
@ConfigurationProperties("file.service.client")
public record FileServiceProperties(
    String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("20s") Duration readTimeout) {}
