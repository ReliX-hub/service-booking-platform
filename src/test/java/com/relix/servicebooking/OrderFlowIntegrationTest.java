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

class OrderFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Full order flow: create -> complete -> settlement exists")
    void fullOrderFlow() throws Exception {
        ResponseEntity<String> providersResponse = restTemplate.getForEntity("/api/providers", String.class);
        assertThat(providersResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(providersResponse.getBody()).contains("\"success\":true");

        ResponseEntity<String> servicesResponse = restTemplate.getForEntity(
                "/api/providers/1/services", String.class);
        assertThat(servicesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> slotsResponse = restTemplate.getForEntity(
                "/api/providers/1/time-slots?status=AVAILABLE", String.class);
        assertThat(slotsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        OrderCreateRequest orderRequest = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .notes("Integration test order")
                .build();

        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/orders", orderRequest, String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).contains("\"success\":true");

        JsonNode createJson = objectMapper.readTree(createResponse.getBody());
        Long orderId = createJson.path("data").path("id").asLong();
        assertThat(orderId).isGreaterThan(0);

        ResponseEntity<String> completeResponse = restTemplate.postForEntity(
                "/api/orders/" + orderId + "/complete", null, String.class);
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completeResponse.getBody()).contains("COMPLETED");

        ResponseEntity<String> settlementResponse = restTemplate.getForEntity(
                "/api/settlements/order/" + orderId, String.class);
        assertThat(settlementResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(settlementResponse.getBody()).contains("SETTLED");
        assertThat(settlementResponse.getBody()).contains("platformFee");
        assertThat(settlementResponse.getBody()).contains("providerPayout");
    }

    @Test
    @DisplayName("Order creation with time slot books the slot")
    void orderWithTimeSlotBooking() throws Exception {
        OrderCreateRequest orderRequest = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .timeSlotId(1L)
                .notes("Order with time slot")
                .build();

        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/orders", orderRequest, String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> slotResponse = restTemplate.getForEntity(
                "/api/time-slots/1", String.class);
        assertThat(slotResponse.getBody()).contains("BOOKED");
    }
}