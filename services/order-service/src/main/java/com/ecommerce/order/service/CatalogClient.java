package com.ecommerce.order.service;

import com.ecommerce.order.dto.CatalogProductResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogClient {
    private final RestClient restClient;

    public CatalogClient(RestClient catalogRestClient) {
        restClient = catalogRestClient;
    }

    public CatalogProductResponse getProduct(String productId) {
        try {
            CatalogProductResponse product = restClient.get()
                    .uri("/api/products/{productId}", productId)
                    .retrieve()
                    .body(CatalogProductResponse.class);
            if (product == null || product.id() == null || product.name() == null || product.price() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Catalog returned an incomplete product");
            }
            return product;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Product does not exist: " + productId);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Catalog service could not validate order products", exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Catalog service is unavailable", exception);
        }
    }
}
