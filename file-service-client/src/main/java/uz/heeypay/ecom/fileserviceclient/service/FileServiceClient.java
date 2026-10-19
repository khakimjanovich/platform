package uz.heeypay.ecom.fileserviceclient.service;

import org.springframework.web.service.annotation.HttpExchange;
import uz.heeypay.ecom.fileservice.api.client.FileServiceApi;

/** Every file service operation, as one client. */
@HttpExchange
public interface FileServiceClient extends FileServiceApi {}
