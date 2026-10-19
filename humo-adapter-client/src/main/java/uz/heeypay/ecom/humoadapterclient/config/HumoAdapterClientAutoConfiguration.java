package uz.heeypay.ecom.humoadapterclient.config;

import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import uz.heeypay.ecom.humoadapterclient.property.HumoAdapterProperties;
import uz.heeypay.ecom.humoadapterclient.service.HumoAdapterClient;

/** Setting {@code humo.adapter.client.base-url} gives the app a {@link HumoAdapterClient}. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "humo.adapter.client", name = "base-url")
@EnableConfigurationProperties(HumoAdapterProperties.class)
public class HumoAdapterClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "humoAdapterRestClient")
  RestClient humoAdapterRestClient(HumoAdapterProperties properties) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build());
    factory.setReadTimeout(properties.readTimeout());
    return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }

  @Bean
  @ConditionalOnMissingBean
  HumoAdapterClient humoAdapterClient(@Qualifier("humoAdapterRestClient") RestClient restClient) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(HumoAdapterClient.class);
  }
}
