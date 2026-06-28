package com.sadna.group13a.domain.policies.purchase;

import com.sadna.group13a.domain.shared.DomainException;
import com.sadna.group13a.domain.shared.PurchaseContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Purchase Policies")
class PurchasePolicyTest {

    private static PurchaseContext ctx(int tickets, int age) {
        return new PurchaseContext("u1", tickets, age, List.of());
    }

    // ── AgeRestrictionPolicy ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AgeRestrictionPolicy")
    class AgeRestrictionPolicyTests {

        @Test
        @DisplayName("user meets minimum age — satisfied")
        void meetsMinAge_satisfied() {
            assertTrue(new AgeRestrictionPolicy(18).isSatisfied(ctx(1, 18)));
        }

        @Test
        @DisplayName("user exceeds minimum age — satisfied")
        void exceedsMinAge_satisfied() {
            assertTrue(new AgeRestrictionPolicy(18).isSatisfied(ctx(1, 25)));
        }

        @Test
        @DisplayName("user is under minimum age — not satisfied")
        void underMinAge_notSatisfied() {
            assertFalse(new AgeRestrictionPolicy(18).isSatisfied(ctx(1, 17)));
        }

        @Test
        @DisplayName("negative minAge — throws")
        void negativeMinAge_throws() {
            assertThrows(DomainException.class, () -> new AgeRestrictionPolicy(-1));
        }

        @Test
        @DisplayName("getMinAge returns configured value")
        void getMinAge() {
            assertEquals(21, new AgeRestrictionPolicy(21).getMinAge());
        }
    }

    // ── MaxTicketsPolicy ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("MaxTicketsPolicy")
    class MaxTicketsPolicyTests {

        @Test
        @DisplayName("ticket count within max — satisfied")
        void withinMax_satisfied() {
            assertTrue(new MaxTicketsPolicy(4).isSatisfied(ctx(4, 0)));
        }

        @Test
        @DisplayName("ticket count below max — satisfied")
        void belowMax_satisfied() {
            assertTrue(new MaxTicketsPolicy(4).isSatisfied(ctx(2, 0)));
        }

        @Test
        @DisplayName("ticket count exceeds max — not satisfied")
        void exceedsMax_notSatisfied() {
            assertFalse(new MaxTicketsPolicy(4).isSatisfied(ctx(5, 0)));
        }

        @Test
        @DisplayName("zero max — throws")
        void zeroMax_throws() {
            assertThrows(DomainException.class, () -> new MaxTicketsPolicy(0));
        }

        @Test
        @DisplayName("getMax returns configured value")
        void getMax() {
            assertEquals(4, new MaxTicketsPolicy(4).getMax());
        }
    }

    // ── MinTicketsPolicy ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("MinTicketsPolicy")
    class MinTicketsPolicyTests {

        @Test
        @DisplayName("ticket count meets minimum — satisfied")
        void meetsMin_satisfied() {
            assertTrue(new MinTicketsPolicy(2).isSatisfied(ctx(2, 0)));
        }

        @Test
        @DisplayName("ticket count exceeds minimum — satisfied")
        void exceedsMin_satisfied() {
            assertTrue(new MinTicketsPolicy(2).isSatisfied(ctx(5, 0)));
        }

        @Test
        @DisplayName("ticket count below minimum — not satisfied")
        void belowMin_notSatisfied() {
            assertFalse(new MinTicketsPolicy(2).isSatisfied(ctx(1, 0)));
        }

        @Test
        @DisplayName("zero min — throws")
        void zeroMin_throws() {
            assertThrows(DomainException.class, () -> new MinTicketsPolicy(0));
        }

        @Test
        @DisplayName("getMin returns configured value")
        void getMin() {
            assertEquals(2, new MinTicketsPolicy(2).getMin());
        }
    }

    // ── OrPolicy ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OrPolicy")
    class OrPolicyTests {

        private final PurchaseContext PASS_AGE = ctx(1, 20);
        private final PurchaseContext FAIL_AGE = ctx(1, 10);

        @Test
        @DisplayName("left satisfied — satisfied")
        void leftSatisfied_satisfied() {
            OrPolicy policy = new OrPolicy(new AgeRestrictionPolicy(18), new MaxTicketsPolicy(1));
            assertTrue(policy.isSatisfied(ctx(5, 20)));
        }

        @Test
        @DisplayName("right satisfied — satisfied")
        void rightSatisfied_satisfied() {
            OrPolicy policy = new OrPolicy(new AgeRestrictionPolicy(18), new MaxTicketsPolicy(5));
            assertTrue(policy.isSatisfied(ctx(3, 10)));
        }

        @Test
        @DisplayName("both satisfied — satisfied")
        void bothSatisfied_satisfied() {
            OrPolicy policy = new OrPolicy(new AgeRestrictionPolicy(18), new MaxTicketsPolicy(5));
            assertTrue(policy.isSatisfied(ctx(3, 20)));
        }

        @Test
        @DisplayName("neither satisfied — not satisfied")
        void neitherSatisfied_notSatisfied() {
            OrPolicy policy = new OrPolicy(new AgeRestrictionPolicy(18), new MaxTicketsPolicy(2));
            assertFalse(policy.isSatisfied(ctx(5, 10)));
        }

        @Test
        @DisplayName("null child — throws")
        void nullChild_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new OrPolicy(null, new AgeRestrictionPolicy(18)));
        }

        @Test
        @DisplayName("getLeft / getRight return the configured policies")
        void getters() {
            AgeRestrictionPolicy left  = new AgeRestrictionPolicy(18);
            MaxTicketsPolicy     right = new MaxTicketsPolicy(4);
            OrPolicy policy = new OrPolicy(left, right);
            assertSame(left,  policy.getLeft());
            assertSame(right, policy.getRight());
        }
    }
}
