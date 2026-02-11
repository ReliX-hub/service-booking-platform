package com.relix.servicebooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIdempotencyTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Same idempotencyKey returns same order and writes audit only once")
    void idempotentOrderCreation() throws Exception {
        String idempotencyKey = "test-idem-" + System.currentTimeMillis();

        OrderCreateRequest request = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .idempotencyKey(idempotencyKey)
                .build();

        ResponseEntity<String> first = restTemplate.postForEntity("/api/orders", request, String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode firstJson = objectMapper.readTree(first.getBody());
        Long firstOrderId = firstJson.path("data").path("id").asLong();

        ResponseEntity<String> second = restTemplate.postForEntity("/api/orders", request, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).contains("Order already exists");

        JsonNode secondJson = objectMapper.readTree(second.getBody());
        Long secondOrderId = secondJson.path("data").path("id").asLong();

        assertThat(secondOrderId).isEqualTo(firstOrderId);

        ResponseEntity<String> auditResponse = restTemplate.getForEntity(
                "/api/audit-logs?entityType=ORDER&entityId=" + firstOrderId, String.class);
        assertThat(auditResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode auditJson = objectMapper.readTree(auditResponse.getBody());
        JsonNode auditData = auditJson.path("data");
        assertThat(auditData.isArray()).isTrue();
        assertThat(auditData.size()).isEqualTo(1);
        assertThat(auditData.get(0).path("action").asText()).isEqualTo("ORDER_CREATED");
    }

    @Test
    @DisplayName("Same idempotencyKey with different payload returns 409")
    void idempotentKeyConflict() throws Exception {
        String idempotencyKey = "conflict-test-" + System.currentTimeMillis();

        OrderCreateRequest request1 = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .idempotencyKey(idempotencyKey)
                .build();
        restTemplate.postForEntity("/api/orders", request1, String.class);

        OrderCreateRequest request2 = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(2L)
                .idempotencyKey(idempotencyKey)
                .build();

        ResponseEntity<String> second = restTemplate.postForEntity("/api/orders", request2, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    @DisplayName("Different idempotencyKey creates different orders")
    void differentIdempotencyKeyCreatesDifferentOrders() throws Exception {
        OrderCreateRequest request1 = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .idempotencyKey("key-1-" + System.currentTimeMillis())
                .build();

        OrderCreateRequest request2 = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .idempotencyKey("key-2-" + System.currentTimeMillis())
                .build();

        ResponseEntity<String> first = restTemplate.postForEntity("/api/orders", request1, String.class);
        ResponseEntity<String> second = restTemplate.postForEntity("/api/orders", request2, String.class);

        JsonNode firstJson = objectMapper.readTree(first.getBody());
        JsonNode secondJson = objectMapper.readTree(second.getBody());

        Long firstOrderId = firstJson.path("data").path("id").asLong();
        Long secondOrderId = secondJson.path("data").path("id").asLong();

        assertThat(secondOrderId).isNotEqualTo(firstOrderId);
    }
}