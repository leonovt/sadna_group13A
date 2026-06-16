package com.sadna.group13a.application;

import com.sadna.group13a.domain.policies.discount.*;
import com.sadna.group13a.domain.policies.purchase.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyFormatterTest {

    // ── Purchase policies ──────────────────────────────────────────

    @Test void allowAll()          { assertEquals("Allow All", PolicyFormatter.describe(new AllowAllPolicy())); }
    @Test void ageRestriction()    { assertEquals("Age ≥ 18",       PolicyFormatter.describe(new AgeRestrictionPolicy(18))); }
    @Test void minTickets()        { assertEquals("Min tickets: 2", PolicyFormatter.describe(new MinTicketsPolicy(2))); }
    @Test void maxTickets()        { assertEquals("Max tickets: 5", PolicyFormatter.describe(new MaxTicketsPolicy(5))); }

    @Test
    void andPolicy() {
        var p = new AndPolicy(new AllowAllPolicy(), new MinTicketsPolicy(1));
        assertTrue(PolicyFormatter.describe(p).startsWith("All of: ["));
    }

    @Test
    void orPolicy() {
        var p = new OrPolicy(new AllowAllPolicy(), new MaxTicketsPolicy(3));
        assertTrue(PolicyFormatter.describe(p).startsWith("Any of: ["));
    }

    @Test void nullPurchase() { assertEquals("None", PolicyFormatter.describe((com.sadna.group13a.domain.shared.PurchasePolicy) null)); }

    // ── Discount policies ──────────────────────────────────────────

    @Test void noDiscount()        { assertEquals("No discount", PolicyFormatter.describe(new NoDiscountPolicy())); }

    @Test
    void simpleDiscount() {
        LocalDate s = LocalDate.of(2025, 1, 1);
        LocalDate e = LocalDate.of(2025, 12, 31);
        String desc = PolicyFormatter.describe(new SimpleDiscount(0.10, s, e));
        assertTrue(desc.contains("10%"), desc);
    }

    @Test
    void couponDiscount() {
        String desc = PolicyFormatter.describe(
                new CouponDiscount(0.15, "SAVE15", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        assertTrue(desc.contains("SAVE15"), desc);
        assertTrue(desc.contains("15%"), desc);
    }

    @Test
    void conditionalDiscount() {
        String desc = PolicyFormatter.describe(new ConditionalDiscount(0.20, 3));
        assertTrue(desc.contains("20%"), desc);
        assertTrue(desc.contains("3"), desc);
    }

    @Test
    void additiveDiscountPolicy() {
        var p = new AdditiveDiscountPolicy(List.of(new NoDiscountPolicy(), new NoDiscountPolicy()));
        assertTrue(PolicyFormatter.describe(p).startsWith("Sum of: ["));
    }

    @Test
    void maxDiscountPolicy() {
        var p = new MaxDiscountPolicy(List.of(new NoDiscountPolicy(), new NoDiscountPolicy()));
        assertTrue(PolicyFormatter.describe(p).startsWith("Best of: ["));
    }

    @Test void nullDiscount() { assertEquals("None", PolicyFormatter.describe((com.sadna.group13a.domain.shared.DiscountPolicy) null)); }
}
