package com.relix.servicebooking.settlement.service;

import com.relix.servicebooking.settlement.dto.BatchResponse;
import com.relix.servicebooking.settlement.entity.Settlement;
import com.relix.servicebooking.settlement.entity.SettlementBatch;
import com.relix.servicebooking.settlement.repository.SettlementBatchRepository;
import com.relix.servicebooking.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementBatchServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementBatchRepository settlementBatchRepository;

    @InjectMocks
    private SettlementBatchService settlementBatchService;

    @Test
    void processBatch_shouldMarkBatchFailed_whenAnySettlementFails() {
        Settlement settlement = Settlement.builder()
                .id(1L)
                .providerPayout(new BigDecimal("50.00"))
                .status(Settlement.SettlementStatus.PENDING)
                .build();

        when(settlementBatchRepository.existsByBatchId(anyString())).thenReturn(false);
        when(settlementRepository.findByStatus(Settlement.SettlementStatus.PENDING))
                .thenReturn(List.of(settlement));

        when(settlementBatchRepository.save(any(SettlementBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 1st save(PROCESSING) succeeds, 2nd save(COMPLETED) fails, 3rd save(FAILED in catch) succeeds.
        when(settlementRepository.save(any(Settlement.class)))
                .thenReturn(settlement)
                .thenThrow(new RuntimeException("mock payout failure"))
                .thenReturn(settlement);

        BatchResponse response = settlementBatchService.processBatch();

        assertEquals("FAILED", response.getStatus());
        assertEquals(0, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
    }
}
