package uz.heeypay.ecom.uzcardadapterclient.service;

import org.springframework.web.service.annotation.HttpExchange;
import uz.heeypay.ecom.uzcardadapter.api.client.UzcardCardServiceApi;
import uz.heeypay.ecom.uzcardadapter.api.client.UzcardTransactionServiceApi;

/** Every Uzcard adapter operation, as one client. */
@HttpExchange
public interface UzcardAdapterClient extends UzcardCardServiceApi, UzcardTransactionServiceApi {}
