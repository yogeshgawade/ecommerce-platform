package com.ecommerce.catalog.product;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @Test
    void createShouldReturn201() throws Exception {
        Product product = product();

        when(productService.create(any(Product.class)))
                .thenReturn(product);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Running Shoes"))
                .andExpect(jsonPath("$.category").value("footwear"));
    }

    @Test
    void findAllShouldReturn200() throws Exception {
        when(productService.findAll()).thenReturn(List.of(product()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Running Shoes"));
    }

    @Test
    void searchShouldDelegateSearchQuery() throws Exception {
        when(productService.search("running"))
                .thenReturn(List.of(product()));

        mockMvc.perform(get("/api/products")
                        .param("search", "running"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Running Shoes"));

        verify(productService).search("running");
        verify(productService, never()).findAll();
    }

    @Test
    void findByIdShouldReturn200() throws Exception {
        when(productService.findById("product-1"))
                .thenReturn(product());

        mockMvc.perform(get("/api/products/product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("product-1"));
    }

    @Test
    void deleteShouldReturn204() throws Exception {
        doNothing().when(productService).delete("product-1");

        mockMvc.perform(delete("/api/products/product-1"))
                .andExpect(status().isNoContent());

        verify(productService).delete("product-1");
    }

    private Product product() {
        Product product = new Product();
        product.setId("product-1");
        product.setName("Running Shoes");
        product.setDescription("Lightweight running shoes");
        product.setCategory("footwear");
        product.setBrand("Acme");
        product.setPrice(new BigDecimal("2999.00"));
        product.setStockQuantity(25);
        product.setAttributes(Map.of("color", "black", "size", "10"));
        return product;
    }
}
