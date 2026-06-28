package com.sadna.group13a.domain.policies.discount;

import com.sadna.group13a.domain.shared.DiscountContext;
import com.sadna.group13a.domain.shared.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Discount Policies")
class DiscountPolicyTest {

    private static DiscountContext ctx(int tickets, String coupon) {
        return new DiscountContext("u1", tickets, coupon == null ? List.of() : List.of(coupon));
    }

    // ── CouponDiscount ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CouponDiscount")
    class CouponDiscountTests {

        @Test
        @DisplayName("valid code within date range — applies percentage")
        void validCode_withinRange_appliesDiscount() {
            LocalDate start = LocalDate.now().minusDays(1);
            LocalDate end   = LocalDate.now().plusDays(1);
            CouponDiscount policy = new CouponDiscount(0.20, "SAVE20", start, end);

            double discount = policy.calculateDiscount(100.0, ctx(1, "SAVE20"));

            assertEquals(20.0, discount, 0.001);
        }

        @Test
        @DisplayName("wrong code — no discount")
        void wrongCode_noDiscount() {
            CouponDiscount policy = new CouponDiscount(0.20, "SAVE20", null, null);
            assertEquals(0.0, policy.calculateDiscount(100.0, ctx(1, "WRONG")), 0.001);
        }

        @Test
        @DisplayName("correct code but before start date — no discount")
        void correctCode_beforeStart_noDiscount() {
            LocalDate start = LocalDate.now().plusDays(5);
            CouponDiscount policy = new CouponDiscount(0.10, "CODE", start, null);
            assertEquals(0.0, policy.calculateDiscount(100.0, ctx(1, "CODE")), 0.001);
        }

        @Test
        @DisplayName("correct code but after end date — no discount")
        void correctCode_afterEnd_noDiscount() {
            LocalDate end = LocalDate.now().minusDays(5);
            CouponDiscount policy = new CouponDiscount(0.10, "CODE", null, end);
            assertEquals(0.0, policy.calculateDiscount(100.0, ctx(1, "CODE")), 0.001);
        }

        @Test
        @DisplayName("open-ended coupon (null dates) — applies when code matches")
        void openEndedCoupon_codeMatches_applies() {
            CouponDiscount policy = new CouponDiscount(0.15, "OPEN", null, null);
            assertEquals(15.0, policy.calculateDiscount(100.0, ctx(1, "OPEN")), 0.001);
        }

        @Test
        @DisplayName("percentage out of range — throws")
        void invalidPercentage_throws() {
            assertThrows(DomainException.class, () -> new CouponDiscount(1.5, "X", null, null));
        }

        @Test
        @DisplayName("blank code — throws")
        void blankCode_throws() {
            assertThrows(DomainException.class, () -> new CouponDiscount(0.1, "  ", null, null));
        }

        @Test
        @DisplayName("end before start — throws")
        void endBeforeStart_throws() {
            assertThrows(DomainException.class,
                    () -> new CouponDiscount(0.1, "X", LocalDate.now(), LocalDate.now().minusDays(1)));
        }

        @Test
        @DisplayName("getters return correct values")
        void getters() {
            CouponDiscount p = new CouponDiscount(0.25, "CODE25", null, null);
            assertEquals(0.25, p.getPercentage(), 0.001);
            assertEquals("CODE25", p.getCode());
        }
    }

    // ── SimpleDiscount ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SimpleDiscount")
    class SimpleDiscountTests {

        @Test
        @DisplayName("today is in range — applies discount")
        void inRange_appliesDiscount() {
            LocalDate start = LocalDate.now().minusDays(1);
            LocalDate end   = LocalDate.now().plusDays(1);
            SimpleDiscount policy = new SimpleDiscount(0.10, start, end);

            assertEquals(10.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("today is before range — no discount")
        void beforeRange_noDiscount() {
            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end   = LocalDate.now().plusDays(5);
            SimpleDiscount policy = new SimpleDiscount(0.10, start, end);

            assertEquals(0.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("today is after range — no discount")
        void afterRange_noDiscount() {
            LocalDate start = LocalDate.now().minusDays(5);
            LocalDate end   = LocalDate.now().minusDays(1);
            SimpleDiscount policy = new SimpleDiscount(0.10, start, end);

            assertEquals(0.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("percentage out of range — throws")
        void invalidPercentage_throws() {
            assertThrows(DomainException.class,
                    () -> new SimpleDiscount(-0.1, LocalDate.now(), LocalDate.now().plusDays(1)));
        }

        @Test
        @DisplayName("null date — throws")
        void nullDate_throws() {
            assertThrows(DomainException.class,
                    () -> new SimpleDiscount(0.1, null, LocalDate.now()));
        }

        @Test
        @DisplayName("end before start — throws")
        void endBeforeStart_throws() {
            assertThrows(DomainException.class,
                    () -> new SimpleDiscount(0.1, LocalDate.now(), LocalDate.now().minusDays(1)));
        }

        @Test
        @DisplayName("getters return correct values")
        void getters() {
            LocalDate start = LocalDate.now().minusDays(1);
            LocalDate end   = LocalDate.now().plusDays(1);
            SimpleDiscount p = new SimpleDiscount(0.30, start, end);
            assertEquals(0.30, p.getPercentage(), 0.001);
            assertEquals(start, p.getStartDate());
            assertEquals(end,   p.getEndDate());
        }
    }

    // ── ConditionalDiscount ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ConditionalDiscount")
    class ConditionalDiscountTests {

        @Test
        @DisplayName("ticket count meets minimum — applies discount")
        void meetsMinimum_appliesDiscount() {
            ConditionalDiscount policy = new ConditionalDiscount(0.10, 3);
            assertEquals(10.0, policy.calculateDiscount(100.0, ctx(3, null)), 0.001);
        }

        @Test
        @DisplayName("ticket count exceeds minimum — applies discount")
        void exceedsMinimum_appliesDiscount() {
            ConditionalDiscount policy = new ConditionalDiscount(0.10, 3);
            assertEquals(10.0, policy.calculateDiscount(100.0, ctx(5, null)), 0.001);
        }

        @Test
        @DisplayName("ticket count below minimum — no discount")
        void belowMinimum_noDiscount() {
            ConditionalDiscount policy = new ConditionalDiscount(0.10, 3);
            assertEquals(0.0, policy.calculateDiscount(100.0, ctx(2, null)), 0.001);
        }

        @Test
        @DisplayName("invalid percentage — throws")
        void invalidPercentage_throws() {
            assertThrows(DomainException.class, () -> new ConditionalDiscount(1.5, 2));
        }

        @Test
        @DisplayName("zero minTickets — throws")
        void zeroMinTickets_throws() {
            assertThrows(DomainException.class, () -> new ConditionalDiscount(0.1, 0));
        }

        @Test
        @DisplayName("getters return correct values")
        void getters() {
            ConditionalDiscount p = new ConditionalDiscount(0.20, 4);
            assertEquals(0.20, p.getPercentage(), 0.001);
            assertEquals(4, p.getMinTickets());
        }
    }

    // ── AdditiveDiscountPolicy ────────────────────────────────────────────────

    @Nested
    @DisplayName("AdditiveDiscountPolicy")
    class AdditiveDiscountPolicyTests {

        @Test
        @DisplayName("single child — returns its discount")
        void singleChild_returnsItsDiscount() {
            ConditionalDiscount child = new ConditionalDiscount(0.10, 1);
            AdditiveDiscountPolicy policy = new AdditiveDiscountPolicy(List.of(child));

            assertEquals(10.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("two children — sums both discounts")
        void twoChildren_sumsDiscounts() {
            ConditionalDiscount ten     = new ConditionalDiscount(0.10, 1);
            ConditionalDiscount twenty  = new ConditionalDiscount(0.20, 1);
            AdditiveDiscountPolicy policy = new AdditiveDiscountPolicy(List.of(ten, twenty));

            assertEquals(30.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("three children — sums all discounts")
        void threeChildren_sumsAll() {
            ConditionalDiscount a = new ConditionalDiscount(0.10, 1);
            ConditionalDiscount b = new ConditionalDiscount(0.15, 1);
            ConditionalDiscount c = new ConditionalDiscount(0.05, 1);
            AdditiveDiscountPolicy policy = new AdditiveDiscountPolicy(List.of(a, b, c));

            assertEquals(30.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("child whose condition is not met contributes zero")
        void childConditionNotMet_contributesZero() {
            ConditionalDiscount active   = new ConditionalDiscount(0.20, 1);
            ConditionalDiscount inactive = new ConditionalDiscount(0.50, 10); // needs 10 tickets
            AdditiveDiscountPolicy policy = new AdditiveDiscountPolicy(List.of(active, inactive));

            assertEquals(20.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("all children return zero — result is zero")
        void allZero_returnsZero() {
            NoDiscountPolicy a = new NoDiscountPolicy();
            NoDiscountPolicy b = new NoDiscountPolicy();
            AdditiveDiscountPolicy policy = new AdditiveDiscountPolicy(List.of(a, b));

            assertEquals(0.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("null children list — throws")
        void nullChildren_throws() {
            assertThrows(IllegalArgumentException.class, () -> new AdditiveDiscountPolicy(null));
        }

        @Test
        @DisplayName("empty children list — throws")
        void emptyChildren_throws() {
            assertThrows(IllegalArgumentException.class, () -> new AdditiveDiscountPolicy(List.of()));
        }

        @Test
        @DisplayName("getChildren returns the supplied list")
        void getChildren_returnsAll() {
            ConditionalDiscount child = new ConditionalDiscount(0.10, 1);
            AdditiveDiscountPolicy policy = new AdditiveDiscountPolicy(List.of(child));

            assertEquals(1, policy.getChildren().size());
            assertEquals(child, policy.getChildren().get(0));
        }
    }

    // ── MaxDiscountPolicy ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("MaxDiscountPolicy")
    class MaxDiscountPolicyTests {

        @Test
        @DisplayName("returns the highest discount among children")
        void returnsHighestDiscount() {
            ConditionalDiscount low  = new ConditionalDiscount(0.10, 1);
            ConditionalDiscount high = new ConditionalDiscount(0.30, 1);
            MaxDiscountPolicy policy = new MaxDiscountPolicy(List.of(low, high));

            assertEquals(30.0, policy.calculateDiscount(100.0, ctx(2, null)), 0.001);
        }

        @Test
        @DisplayName("single child — returns its discount")
        void singleChild_returnsItsDiscount() {
            ConditionalDiscount child = new ConditionalDiscount(0.15, 1);
            MaxDiscountPolicy policy = new MaxDiscountPolicy(List.of(child));

            assertEquals(15.0, policy.calculateDiscount(100.0, ctx(1, null)), 0.001);
        }

        @Test
        @DisplayName("empty children list — throws")
        void emptyChildren_throws() {
            assertThrows(IllegalArgumentException.class, () -> new MaxDiscountPolicy(List.of()));
        }

        @Test
        @DisplayName("null children — throws")
        void nullChildren_throws() {
            assertThrows(IllegalArgumentException.class, () -> new MaxDiscountPolicy(null));
        }

        @Test
        @DisplayName("getChildren returns the list")
        void getChildren() {
            ConditionalDiscount child = new ConditionalDiscount(0.10, 1);
            MaxDiscountPolicy p = new MaxDiscountPolicy(List.of(child));
            assertEquals(1, p.getChildren().size());
        }
    }
}
