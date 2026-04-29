package com.sadna.group13a.domain.service;

import com.sadna.group13a.domain.company.ProductionCompany;
import com.sadna.group13a.domain.event.Event;
import com.sadna.group13a.domain.event.Seat;
import com.sadna.group13a.domain.event.SeatedZone;
import com.sadna.group13a.domain.external.IPaymentGateway;
import com.sadna.group13a.domain.external.ITicketSupplier;
import com.sadna.group13a.domain.order.ActiveOrder;
import com.sadna.group13a.domain.order.OrderHistory;
import com.sadna.group13a.domain.order.OrderItem;
import com.sadna.group13a.domain.policy.DiscountPolicy;
import com.sadna.group13a.domain.policy.PurchasePolicy;
import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.SeatStatus;
import com.sadna.group13a.domain.shared.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckoutDomainService Tests with Mocks")
class CheckoutDomainServiceTest {

    @Mock
    private IPaymentGateway paymentGateway;

    @Mock
    private ITicketSupplier ticketSupplier;

    @Mock
    private PurchasePolicy purchasePolicy;

    @Mock
    private DiscountPolicy discountPolicy;

    @Mock
    private Event event;

    @Mock
    private ProductionCompany company;

    @Mock
    private SeatedZone seatedZone;

    private CheckoutDomainService service;
    private ActiveOrder activeOrder;
    private Seat seat;

    @BeforeEach
    void setUp() {
        service = new CheckoutDomainService(paymentGateway, ticketSupplier);
        activeOrder = new ActiveOrder("order-1", "user-1", UserType.MEMBER);
        
        seat = new Seat("seat-1", "A-1");
        seat.hold("user-1"); // Seat must be HELD by the user to be sold
    }

    @Test
    @DisplayName("Given valid order and stubs — When checkout — Then returns OrderHistory and sells seat")
    void GivenValidOrder_WhenCheckout_ThenSuccess() {
        // Arrange
        OrderItem item = new OrderItem("event-1", "zone-1", "seat-1", 100.0);
        activeOrder.addItem(item);

        when(purchasePolicy.isSatisfied()).thenReturn(true);
        when(discountPolicy.calculateDiscount(100.0)).thenReturn(20.0);
        
        // Final price = 100 - 20 = 80
        when(paymentGateway.processPayment(eq("user-1"), eq(80.0), anyString())).thenReturn("txn-123");
        
        when(event.getZoneById("zone-1")).thenReturn(seatedZone);
        when(seatedZone.findSeatById("seat-1")).thenReturn(java.util.Optional.of(seat));
        
        when(event.getId()).thenReturn("event-1");
        when(event.getTitle()).thenReturn("Test Event");
        when(event.getEventDate()).thenReturn(LocalDateTime.now().plusDays(1));
        when(company.getId()).thenReturn("company-1");
        when(company.getName()).thenReturn("Test Company");
        when(seatedZone.getName()).thenReturn("VIP Zone");

        when(ticketSupplier.generateTicket("order-1", "event-1", "seat-1")).thenReturn("BARCODE-999");

        // Act
        OrderHistory history = service.checkout(activeOrder, event, company, purchasePolicy, discountPolicy, "card-info");

        // Assert
        assertNotNull(history);
        assertEquals(80.0, history.getTotalPaid());
        assertEquals(1, history.getItems().size());
        assertEquals("user-1", history.getUserId());
        
        // Verify Seat was sold
        assertEquals(SeatStatus.SOLD, seat.getStatus());
        
        // Verify external mocks were called
        verify(paymentGateway).processPayment("user-1", 80.0, "card-info");
        verify(ticketSupplier).generateTicket("order-1", "event-1", "seat-1");
    }

    @Test
    @DisplayName("Given unsatisfied policy — When checkout — Then throws Exception and does not charge")
    void GivenUnsatisfiedPolicy_WhenCheckout_ThenThrows() {
        // Arrange
        when(purchasePolicy.isSatisfied()).thenReturn(false);

        // Act & Assert
        assertThrows(DomainException.class, () -> 
            service.checkout(activeOrder, event, company, purchasePolicy, discountPolicy, "card-info"));
            
        verify(paymentGateway, never()).processPayment(anyString(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("Given payment failure — When checkout — Then throws Exception and does not sell seat")
    void GivenPaymentFailure_WhenCheckout_ThenThrows() {
        // Arrange
        OrderItem item = new OrderItem("event-1", "zone-1", "seat-1", 100.0);
        activeOrder.addItem(item);

        when(paymentGateway.processPayment(eq("user-1"), eq(100.0), anyString())).thenReturn(null);

        // Act & Assert
        assertThrows(DomainException.class, () -> 
            service.checkout(activeOrder, event, company, null, null, "card-info"));
            
        assertEquals(SeatStatus.HELD, seat.getStatus()); // Seat remains held, not sold
        verify(ticketSupplier, never()).generateTicket(anyString(), anyString(), anyString());
    }
}
