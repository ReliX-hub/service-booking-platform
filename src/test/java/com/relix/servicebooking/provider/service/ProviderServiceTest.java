package com.relix.servicebooking.provider.service;

import com.relix.servicebooking.common.exception.ResourceNotFoundException;
import com.relix.servicebooking.provider.dto.ProviderProfileUpsertRequest;
import com.relix.servicebooking.provider.dto.ProviderResponse;
import com.relix.servicebooking.provider.entity.Provider;
import com.relix.servicebooking.provider.repository.ProviderRepository;
import com.relix.servicebooking.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceTest {

    @Mock private ProviderRepository providerRepository;

    @InjectMocks private ProviderService providerService;

    private User createUser(Long id) {
        User u = User.builder().email("p@test.com").name("P").passwordHash("h")
                .role(User.UserRole.PROVIDER).status(User.UserStatus.ACTIVE).build();
        u.setId(id);
        return u;
    }

    private Provider createProvider(Long id, User user) {
        Provider p = Provider.builder().user(user).businessName("Biz")
                .description("Desc").address("123 St").build();
        p.setId(id);
        return p;
    }

    @Test
    @DisplayName("getProviderById returns response for existing provider")
    void getById_success() {
        User user = createUser(1L);
        Provider provider = createProvider(10L, user);

        when(providerRepository.findById(10L)).thenReturn(Optional.of(provider));

        ProviderResponse resp = providerService.getProviderById(10L);

        assertEquals(10L, resp.getId());
        assertEquals("Biz", resp.getBusinessName());
        assertEquals(1L, resp.getUserId());
    }

    @Test
    @DisplayName("getProviderById throws when not found")
    void getById_notFound_throws() {
        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> providerService.getProviderById(999L));
    }

    @Test
    @DisplayName("upsertProviderProfile creates new profile when none exists")
    void upsert_createsNew() {
        User user = createUser(1L);

        when(providerRepository.findByUser_Id(1L)).thenReturn(Optional.empty());
        when(providerRepository.save(any(Provider.class))).thenAnswer(inv -> {
            Provider p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        ProviderProfileUpsertRequest request = new ProviderProfileUpsertRequest();
        request.setBusinessName("New Biz");
        request.setDescription("New Desc");
        request.setAddress("456 Ave");

        ProviderResponse resp = providerService.upsertProviderProfile(user, request);

        assertEquals("New Biz", resp.getBusinessName());
    }

    @Test
    @DisplayName("upsertProviderProfile updates existing profile")
    void upsert_updatesExisting() {
        User user = createUser(1L);
        Provider existing = createProvider(10L, user);

        when(providerRepository.findByUser_Id(1L)).thenReturn(Optional.of(existing));
        when(providerRepository.save(any(Provider.class))).thenAnswer(inv -> inv.getArgument(0));

        ProviderProfileUpsertRequest request = new ProviderProfileUpsertRequest();
        request.setBusinessName("Updated Biz");
        request.setDescription("Updated Desc");
        request.setAddress("789 Blvd");

        ProviderResponse resp = providerService.upsertProviderProfile(user, request);

        assertEquals("Updated Biz", resp.getBusinessName());
        assertEquals("Updated Desc", resp.getDescription());
    }

    @Test
    @DisplayName("getAllProviders returns list")
    void getAll_returnsList() {
        User u1 = createUser(1L);
        User u2 = createUser(2L);
        u2.setEmail("p2@test.com");

        when(providerRepository.findAll()).thenReturn(List.of(
                createProvider(10L, u1), createProvider(11L, u2)));

        List<ProviderResponse> result = providerService.getAllProviders();

        assertEquals(2, result.size());
    }
}
