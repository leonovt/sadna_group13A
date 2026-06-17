package com.sadna.group13a.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sadna.group13a.domain.Aggregates.Complaint.Complaint;
import com.sadna.group13a.domain.Aggregates.Complaint.ComplaintStatus;
import com.sadna.group13a.domain.Aggregates.Inquiry.Inquiry;
import com.sadna.group13a.domain.Aggregates.Inquiry.InquiryStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Domain behaviour + JSON round-trip for {@link Complaint} and {@link Inquiry}. The mapper
 * here mirrors {@code PersistenceConfig#domainObjectMapper} so it proves the aggregates
 * serialize and deserialize through the JSON-blob persistence path.
 */
class ComplaintInquiryAggregateTest {

    private static ObjectMapper domainMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.CREATOR, Visibility.ANY);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Test
    void complaint_respond_setsResolvedStateAndBumpsVersion() {
        Complaint c = new Complaint("c1", "u1", "Scalping", "bots", LocalDateTime.now());
        assertEquals(ComplaintStatus.OPEN, c.getStatus());
        long before = c.getVersion();

        c.respond("admin1", "We refunded you.");

        assertEquals(ComplaintStatus.RESOLVED, c.getStatus());
        assertEquals("We refunded you.", c.getAdminResponse());
        assertEquals("admin1", c.getResolvedByAdminId());
        assertNotNull(c.getResolvedAt());
        assertTrue(c.getVersion() > before);
    }

    @Test
    void complaint_respond_rejectsDoubleResolveAndBlank() {
        Complaint c = new Complaint("c1", "u1", "s", "m", LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> c.respond("a", " "));
        c.respond("a", "done");
        assertThrows(IllegalStateException.class, () -> c.respond("a", "again"));
    }

    @Test
    void complaint_jsonRoundTrip() throws Exception {
        ObjectMapper m = domainMapper();
        Complaint c = new Complaint("c1", "u1", "Subj", "Body", LocalDateTime.now());
        c.respond("admin1", "handled");

        Complaint back = m.readValue(m.writeValueAsString(c), Complaint.class);

        assertEquals("c1", back.getId());
        assertEquals("u1", back.getComplainantUserId());
        assertEquals(ComplaintStatus.RESOLVED, back.getStatus());
        assertEquals("handled", back.getAdminResponse());
        assertEquals(c.getVersion(), back.getVersion());
    }

    @Test
    void inquiry_answer_setsAnsweredStateAndRoundTrips() throws Exception {
        Inquiry i = new Inquiry("i1", "buyer1", "co1", "When on sale?", LocalDateTime.now());
        assertEquals(InquiryStatus.OPEN, i.getStatus());
        i.answer("owner1", "Friday at noon.");
        assertEquals(InquiryStatus.ANSWERED, i.getStatus());
        assertThrows(IllegalStateException.class, () -> i.answer("owner1", "again"));

        ObjectMapper m = domainMapper();
        Inquiry back = m.readValue(m.writeValueAsString(i), Inquiry.class);
        assertEquals("buyer1", back.getFromUserId());
        assertEquals("co1", back.getCompanyId());
        assertEquals(InquiryStatus.ANSWERED, back.getStatus());
        assertEquals("Friday at noon.", back.getResponse());
    }
}
