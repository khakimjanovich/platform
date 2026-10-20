package uz.heeypay.ecom.uzcardadapterclient.config;

import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
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
import uz.heeypay.ecom.uzcardadapterclient.property.UzcardAdapterProperties;
import uz.heeypay.ecom.uzcardadapterclient.service.UzcardAdapterClient;

/** Setting {@code uzcard.adapter.client.base-url} gives the app a {@link UzcardAdapterClient}. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "uzcard.adapter.client", name = "base-url")
@EnableConfigurationProperties(UzcardAdapterProperties.class)
public class UzcardAdapterClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "uzcardAdapterRestClient")
  RestClient uzcardAdapterRestClient(
      UzcardAdapterProperties properties, ObjectProvider<RestClient.Builder> builders) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build());
    factory.setReadTimeout(properties.readTimeout());
    return builders
        .getIfAvailable(RestClient::builder)
        .baseUrl(properties.baseUrl())
        .requestFactory(factory)
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  UzcardAdapterClient uzcardAdapterClient(
      @Qualifier("uzcardAdapterRestClient") RestClient restClient) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(UzcardAdapterClient.class);
  }
}
