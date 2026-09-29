package com.ecommerce.catalog.product;

import com.ecommerce.catalog.security.JwtAuthenticationFilter;
import com.ecommerce.catalog.security.SecurityConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm")
class ProductControllerTest {

    private static final String TEST_SECRET =
            "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void adminCanCreateProductAndServerControlsItsIdentity() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"attacker-id","name":"Running Shoes","description":"Lightweight",
                                 "category":"footwear","brand":"Acme","price":2999.00,
                                 "stockQuantity":999,"createdAt":"2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("server-id"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-28T00:00:00Z"))
                .andExpect(jsonPath("$.stockQuantity").doesNotExist());
        verify(productService).create(any(ProductRequest.class));
    }

    @Test
    void anonymousCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }

    @Test
    void invalidBearerTokenCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer not-a-signed-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productService);
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    @Test
    void productListingIsPublicAndPaged() throws Exception {
        when(productService.findAll(0, 20)).thenReturn(new ProductPageResponse<>(
                List.of(response()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Running Shoes"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void searchPassesPaginationToService() throws Exception {
        when(productService.search("running", 1, 10)).thenReturn(new ProductPageResponse<>(
                List.of(response()), 1, 10, 11, 2));

        mockMvc.perform(get("/api/products")
                        .param("search", "running")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(productService).search("running", 1, 10);
    }

    @Test
    void adminCanDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/product-1")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
        verify(productService).delete("product-1");
    }

    @Test
    void customerCannotDeleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/product-1")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        when(productService.findAll(0, 101)).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid size"));

        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidProductPrice() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson().replace("2999.00", "-1.00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productService);
    }

    private String validRequestJson() {
        return """
                {"name":"Running Shoes","description":"Lightweight running shoes",
                 "category":"footwear","brand":"Acme","price":2999.00,
                 "attributes":{"color":"black","size":"10"}}
                """;
    }

    private String bearer(String role) {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("admin-user")
                .claim("roles", new String[]{role})
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }

    private ProductResponse response() {
        return new ProductResponse(
                "server-id", "Running Shoes", "Lightweight running shoes", "footwear", "Acme",
                new BigDecimal("2999.00"), Map.of("color", "black"),
                Instant.parse("2026-09-28T00:00:00Z"), Instant.parse("2026-09-28T00:00:00Z")
        );
    }
}
