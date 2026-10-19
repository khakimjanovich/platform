package uz.heeypay.ecom.fileserviceclient.config;

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
import uz.heeypay.ecom.fileserviceclient.property.FileServiceProperties;
import uz.heeypay.ecom.fileserviceclient.service.FileServiceClient;

/** Setting {@code file.service.client.base-url} gives the app a {@link FileServiceClient}. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "file.service.client", name = "base-url")
@EnableConfigurationProperties(FileServiceProperties.class)
public class FileServiceClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "fileServiceRestClient")
  RestClient fileServiceRestClient(FileServiceProperties properties) {
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
  FileServiceClient fileServiceClient(@Qualifier("fileServiceRestClient") RestClient restClient) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(FileServiceClient.class);
  }
}
