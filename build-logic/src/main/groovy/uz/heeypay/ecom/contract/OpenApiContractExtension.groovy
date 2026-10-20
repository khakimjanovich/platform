package uz.heeypay.ecom.contract

import org.gradle.api.provider.Property

/** What an API contract publishes besides server interfaces and models. */
abstract class OpenApiContractExtension {

    /**
     * Also `@HttpExchange` interfaces: what other modules and services call. The owning module
     * serves them as controllers; a caller in the same app injects them, a caller elsewhere gets an
     * HTTP client of the same interface.
     */
    abstract Property<Boolean> getClient()

    /** The generator's own option: false makes `@HttpExchange` methods return the model itself. */
    abstract Property<Boolean> getUseResponseEntity()
}
