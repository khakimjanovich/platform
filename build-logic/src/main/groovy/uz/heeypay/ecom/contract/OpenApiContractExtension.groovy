package uz.heeypay.ecom.contract

import org.gradle.api.provider.Property

/** What an API contract publishes besides server interfaces and models. */
abstract class OpenApiContractExtension {

    /** Also `@HttpExchange` client interfaces, for services other apps call (the adapters). */
    abstract Property<Boolean> getClient()
}
