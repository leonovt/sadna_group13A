package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Complaint.Complaint;
import com.sadna.group13a.domain.Aggregates.Complaint.ComplaintStatus;
import com.sadna.group13a.domain.Aggregates.Inquiry.Inquiry;
import com.sadna.group13a.domain.Aggregates.Inquiry.InquiryStatus;
import com.sadna.group13a.infrastructure.config.PersistenceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end persistence for the complaint/inquiry aggregates through the JSON-blob
 * repositories and a real (H2) datasource — verifies SL-7 round-trip and the lookups
 * used by the services.
 */
@DataJpaTest
@Import({ComplaintRepositoryImpl.class, InquiryRepositoryImpl.class, PersistenceConfig.class})
class ComplaintInquiryRepositoryImplTest {

    @Autowired private ComplaintRepositoryImpl complaints;
    @Autowired private InquiryRepositoryImpl inquiries;

    @Test
    void complaint_roundTripsAndFindsByComplainant() {
        Complaint c = new Complaint("c-1", "user-1", "Scalping", "bots", LocalDateTime.now());
        complaints.save(c);

        Optional<Complaint> found = complaints.findById("c-1");
        assertTrue(found.isPresent());
        assertEquals(ComplaintStatus.OPEN, found.get().getStatus());

        List<Complaint> mine = complaints.findByComplainantUserId("user-1");
        assertEquals(1, mine.size());
        assertTrue(complaints.findByComplainantUserId("nobody").isEmpty());
    }

    @Test
    void complaint_respondPersistsResolution_andFindAllReturnsIt() {
        Complaint c = new Complaint("c-2", "user-2", "Fraud", "fake", LocalDateTime.now());
        complaints.save(c);
        c.respond("admin-1", "Refunded.");
        complaints.save(c);

        Complaint reloaded = complaints.findById("c-2").orElseThrow();
        assertEquals(ComplaintStatus.RESOLVED, reloaded.getStatus());
        assertEquals("Refunded.", reloaded.getAdminResponse());
        assertEquals(1, complaints.findAll().size());
    }

    @Test
    void inquiry_roundTripsAndFindsByCompanyAndSender() {
        Inquiry i = new Inquiry("i-1", "buyer-1", "co-1", "When on sale?", LocalDateTime.now());
        inquiries.save(i);

        assertTrue(inquiries.findById("i-1").isPresent());
        assertEquals(1, inquiries.findByCompanyId("co-1").size());
        assertEquals(1, inquiries.findByFromUserId("buyer-1").size());
        assertTrue(inquiries.findByCompanyId("co-2").isEmpty());

        i.answer("owner-1", "Friday.");
        inquiries.save(i);
        assertEquals(InquiryStatus.ANSWERED, inquiries.findById("i-1").orElseThrow().getStatus());
    }
}
