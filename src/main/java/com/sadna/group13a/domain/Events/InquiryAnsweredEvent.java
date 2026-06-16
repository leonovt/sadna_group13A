package com.sadna.group13a.domain.Events;

/**
 * Published when a production company answers a buyer's inquiry (II.4.4), so the
 * buyer is notified in real time (or on next login if offline — I.5 / I.6).
 */
public record InquiryAnsweredEvent(String userId, String companyName, String response) {
}
