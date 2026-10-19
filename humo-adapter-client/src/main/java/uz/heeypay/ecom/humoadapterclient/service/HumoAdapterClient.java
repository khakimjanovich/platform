package uz.heeypay.ecom.humoadapterclient.service;

import org.springframework.web.service.annotation.HttpExchange;
import uz.heeypay.ecom.humoadapter.api.client.HumoMiddlewareServiceApi;
import uz.heeypay.ecom.humoadapter.api.client.HumoPaymentServiceApi;

/** Every Humo adapter operation, as one client. */
@HttpExchange
public interface HumoAdapterClient extends HumoPaymentServiceApi, HumoMiddlewareServiceApi {}
