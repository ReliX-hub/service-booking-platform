package com.relix.servicebooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.servicebooking.order.dto.OrderCreateRequest;
import com.relix.servicebooking.payment.dto.PaymentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIdempotencyTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Same requestId returns alreadyPaid=true and writes audit only once")
    void idempotentPayment() throws Exception {
        OrderCreateRequest orderRequest = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .idempotencyKey("pay-test-" + System.currentTimeMillis())
                .build();

        ResponseEntity<String> orderResponse = restTemplate.postForEntity("/api/orders", orderRequest, String.class);
        JsonNode orderJson = objectMapper.readTree(orderResponse.getBody());
        Long orderId = orderJson.path("data").path("id").asLong();

        String requestId = "pay-req-" + System.currentTimeMillis();
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .requestId(requestId)
                .build();

        ResponseEntity<String> first = restTemplate.postForEntity(
                "/api/orders/" + orderId + "/pay", paymentRequest, String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode firstJson = objectMapper.readTree(first.getBody());
        assertThat(firstJson.path("data").path("alreadyPaid").asBoolean()).isFalse();
        assertThat(firstJson.path("message").asText()).isEqualTo("Payment confirmed");

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/orders/" + orderId + "/pay", paymentRequest, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode secondJson = objectMapper.readTree(second.getBody());
        assertThat(secondJson.path("data").path("alreadyPaid").asBoolean()).isTrue();
        assertThat(secondJson.path("data").path("requestIdMatched").asBoolean()).isTrue();

        ResponseEntity<String> auditResponse = restTemplate.getForEntity(
                "/api/audit-logs?entityType=ORDER&entityId=" + orderId, String.class);

        JsonNode auditJson = objectMapper.readTree(auditResponse.getBody());
        JsonNode auditData = auditJson.path("data");

        long paymentAuditCount = 0;
        for (JsonNode log : auditData) {
            if ("PAYMENT_CONFIRMED".equals(log.path("action").asText())) {
                paymentAuditCount++;
            }
        }
        assertThat(paymentAuditCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Different requestId on same paid order returns requestIdMatched=false")
    void differentRequestIdOnPaidOrder() throws Exception {
        OrderCreateRequest orderRequest = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .build();

        ResponseEntity<String> orderResponse = restTemplate.postForEntity("/api/orders", orderRequest, String.class);
        JsonNode orderJson = objectMapper.readTree(orderResponse.getBody());
        Long orderId = orderJson.path("data").path("id").asLong();

        PaymentRequest paymentRequest1 = PaymentRequest.builder()
                .requestId("req-1-" + System.currentTimeMillis())
                .build();
        restTemplate.postForEntity("/api/orders/" + orderId + "/pay", paymentRequest1, String.class);

        PaymentRequest paymentRequest2 = PaymentRequest.builder()
                .requestId("req-2-" + System.currentTimeMillis())
                .build();

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/orders/" + orderId + "/pay", paymentRequest2, String.class);

        JsonNode secondJson = objectMapper.readTree(second.getBody());
        assertThat(secondJson.path("data").path("alreadyPaid").asBoolean()).isTrue();
        assertThat(secondJson.path("data").path("requestIdMatched").asBoolean()).isFalse();
        assertThat(secondJson.path("message").asText()).contains("different requestId");
    }

    @Test
    @DisplayName("Payment changes order status to PAID")
    void paymentChangesOrderStatus() throws Exception {
        OrderCreateRequest orderRequest = OrderCreateRequest.builder()
                .customerId(2L)
                .serviceId(1L)
                .build();

        ResponseEntity<String> orderResponse = restTemplate.postForEntity("/api/orders", orderRequest, String.class);
        JsonNode orderJson = objectMapper.readTree(orderResponse.getBody());
        Long orderId = orderJson.path("data").path("id").asLong();

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .requestId("status-test-" + System.currentTimeMillis())
                .build();

        restTemplate.postForEntity("/api/orders/" + orderId + "/pay", paymentRequest, String.class);

        ResponseEntity<String> getOrder = restTemplate.getForEntity("/api/orders/" + orderId, String.class);
        JsonNode getOrderJson = objectMapper.readTree(getOrder.getBody());
        assertThat(getOrderJson.path("data").path("status").asText()).isEqualTo("PAID");
    }
}