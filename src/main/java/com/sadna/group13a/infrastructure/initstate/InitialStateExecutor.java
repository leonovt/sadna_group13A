package com.sadna.group13a.infrastructure.initstate;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.ZoneCreationDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.OrderService;
import com.sadna.group13a.application.Services.UserService;
import com.sadna.group13a.domain.Aggregates.Company.CompanyPermission;
import com.sadna.group13a.domain.Aggregates.Event.EventSaleMode;
import com.sadna.group13a.domain.Aggregates.Event.ZoneType;
import com.sadna.group13a.domain.policies.discount.CouponDiscount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executes a parsed initial-state sequence by replaying use-cases through the
 * <b>application layer only</b> (V3 issue #224).
 *
 * <p>Execution is all-or-nothing: the first failed operation throws an
 * {@link InitialStateException}, aborting the remaining operations.</p>
 *
 * <p>Supported actions and their arguments:</p>
 * <ul>
 *   <li>{@code register}       — username, password</li>
 *   <li>{@code login}          — username, password            (bindTo: auth token)</li>
 *   <li>{@code enter-as-guest} — (none)                        (bindTo: guest token)</li>
 *   <li>{@code create-company} — token, name, [description]    (bindTo: company id)</li>
 *   <li>{@code create-event}   — token, companyId, title, [description], date,
 *                                 [category], [artist], [location]  (bindTo: event id)</li>
 *   <li>{@code publish-event}  — token, eventId</li>
 *   <li>{@code logout}         — token</li>
 *   <li>{@code appoint-owner}  — token, companyId, targetUsername</li>
 *   <li>{@code appoint-manager}— token, companyId, targetUsername, [permissions]
 *                                 (comma-separated: MANAGE_EVENTS,MANAGE_POLICIES,…)</li>
 *   <li>{@code accept-nomination} — token, companyId</li>
 *   <li>{@code reject-nomination} — token, companyId</li>
 *   <li>{@code suspend-company} — token, companyId</li>
 *   <li>{@code reopen-company}  — token, companyId</li>
 *   <li>{@code set-sale-mode}   — token, eventId, mode (REGULAR | QUEUE | RAFFLE)</li>
 *   <li>{@code add-to-cart}     — token, eventId, zoneId, [seatId], [quantity]  (bindTo: order id)</li>
 *   <li>{@code checkout}        — token, orderId, [authCode], paymentDetails  (bindTo: receipt id)</li>
 *   <li>{@code create-venue-map} — token, eventId, [venueName], zones (JSON array of {name,type,basePrice,capacity})</li>
 *   <li>{@code set-company-coupon-discount} — token, companyId, code, percentage (0.0–1.0)</li>
 * </ul>
 *
 * <p>This set is intentionally broad enough to drive a representative series of use-case
 * stories so the system can be initialised into <i>any</i> required state (I.1), through the
 * application layer only, with strict all-or-nothing semantics.</p>
 *
 * <p>Any argument value equal to a previously bound name is resolved to that bound value,
 * e.g. {@code "token": "alice_token"}.</p>
 */
@Component
public class InitialStateExecutor {

    private static final Logger logger = LoggerFactory.getLogger(InitialStateExecutor.class);

    private final UserService userService;
    private final CompanyService companyService;
    private final EventService eventService;
    private final OrderService orderService;

    public InitialStateExecutor(UserService userService,
                                CompanyService companyService,
                                EventService eventService,
                                OrderService orderService) {
        this.userService = userService;
        this.companyService = companyService;
        this.eventService = eventService;
        this.orderService = orderService;
    }

    /** Runs every operation in order; throws on the first failure. */
    public void execute(List<InitOperation> operations) {
        Map<String, String> bindings = new HashMap<>();
        for (int i = 0; i < operations.size(); i++) {
            Ctx ctx = new Ctx(operations.get(i), bindings);
            try {
                dispatch(ctx);
            } catch (InitialStateException e) {
                throw new InitialStateException(
                        "Initial-state operation #" + i + " ('" + ctx.op.action() + "'): " + e.getMessage(), e);
            } catch (Exception e) {
                throw new InitialStateException(
                        "Initial-state operation #" + i + " ('" + ctx.op.action() + "') errored: " + e.getMessage(), e);
            }
        }
        logger.info("Initial-state loaded: {} operation(s) executed successfully.", operations.size());
    }

    private void dispatch(Ctx c) {
        switch (c.op.action()) {
            case "register" -> require(c, userService.register(c.arg("username"), c.arg("password")));

            case "login" -> {
                Result<String> r = userService.login(c.arg("username"), c.arg("password"));
                require(c, r);
                c.bind(r.getOrThrow());
            }

            case "enter-as-guest" -> {
                Result<String> r = userService.enterAsGuest();
                require(c, r);
                c.bind(r.getOrThrow());
            }

            case "create-company" -> {
                String token = c.arg("token");
                String name = c.arg("name");
                require(c, companyService.createCompany(token, name, c.optArg("description", "")));
                if (c.hasBind()) {
                    c.bind(resolveCompanyId(c, token, name));
                }
            }

            case "create-event" -> {
                Result<String> r = eventService.createEvent(
                        c.arg("token"), c.arg("companyId"), c.arg("title"), c.optArg("description", ""),
                        LocalDateTime.parse(c.arg("date")), c.optArg("category", ""),
                        c.optArg("artist", ""), c.optArg("location", ""));
                require(c, r);
                c.bind(r.getOrThrow());
            }

            case "publish-event" -> require(c, eventService.publishEvent(c.arg("token"), c.arg("eventId")));

            case "logout" -> require(c, userService.logout(c.arg("token")));

            case "appoint-owner" -> require(c, companyService.appointOwner(
                    c.arg("token"), c.arg("companyId"), c.arg("targetUsername")));

            case "appoint-manager" -> require(c, companyService.appointManager(
                    c.arg("token"), c.arg("companyId"), c.arg("targetUsername"),
                    parsePermissions(c.optArg("permissions", ""))));

            case "accept-nomination" -> require(c, companyService.acceptNomination(
                    c.arg("token"), c.arg("companyId")));

            case "reject-nomination" -> require(c, companyService.rejectNomination(
                    c.arg("token"), c.arg("companyId")));

            case "suspend-company" -> require(c, companyService.suspendCompany(
                    c.arg("token"), c.arg("companyId")));

            case "reopen-company" -> require(c, companyService.reopenCompany(
                    c.arg("token"), c.arg("companyId")));

            case "set-sale-mode" -> require(c, eventService.setSaleMode(
                    c.arg("token"), c.arg("eventId"), parseSaleMode(c.arg("mode"))));

            case "add-to-cart" -> {
                String seatId = c.optArg("seatId", "");
                Result<String> r;
                if (!seatId.isBlank()) {
                    r = orderService.addBatchItemsToCart(
                            c.arg("token"), c.arg("eventId"), c.arg("zoneId"), List.of(seatId), null);
                } else {
                    int quantity = parsePositiveInt(c.optArg("quantity", "1"), "quantity");
                    r = orderService.addBatchItemsToCart(
                            c.arg("token"), c.arg("eventId"), c.arg("zoneId"), null, quantity);
                }
                require(c, r);
                c.bind(r.getOrThrow());
            }

            case "checkout" -> {
                Result<?> r = orderService.executeCheckout(
                        c.arg("token"), c.arg("orderId"),
                        c.optArg("authCode", null), c.arg("paymentDetails"));
                require(c, r);
            }

            case "create-venue-map" -> {
                Object zonesRaw = c.op.args().get("zones");
                if (zonesRaw == null) {
                    throw new InitialStateException("missing required argument 'zones'.");
                }
                List<ZoneCreationDTO> zones = parseZones(zonesRaw, c.op.action());
                require(c, eventService.createVenueMap(
                        c.arg("token"), c.arg("eventId"),
                        c.optArg("venueName", "Main Venue"), zones));
            }

            case "set-company-coupon-discount" -> {
                double percentage = parseDouble(c.arg("percentage"), "percentage");
                require(c, companyService.setDiscountPolicy(
                        c.arg("token"), c.arg("companyId"),
                        new CouponDiscount(percentage, c.arg("code"), null, null)));
            }

            default -> throw new InitialStateException("Unknown action '" + c.op.action() + "'.");
        }
    }

    private String resolveCompanyId(Ctx c, String token, String name) {
        Result<List<CompanyDTO>> mine = companyService.getMyCompanies(token);
        require(c, mine);
        return mine.getOrThrow().stream()
                .filter(comp -> name.equals(comp.name()))
                .map(CompanyDTO::id)
                .reduce((first, second) -> second) // most recently created with this name
                .orElseThrow(() -> new InitialStateException(
                        "Company '" + name + "' was created but could not be found to bind its id."));
    }

    private void require(Ctx c, Result<?> result) {
        if (!result.isSuccess()) {
            throw new InitialStateException(result.getErrorMessage());
        }
    }

    private Set<CompanyPermission> parsePermissions(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        try {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> CompanyPermission.valueOf(s.toUpperCase()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IllegalArgumentException e) {
            throw new InitialStateException("invalid permission in '" + raw + "': " + e.getMessage());
        }
    }

    private EventSaleMode parseSaleMode(String raw) {
        try {
            return EventSaleMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InitialStateException("invalid sale mode '" + raw + "' (expected REGULAR, QUEUE or RAFFLE)");
        }
    }

    private int parsePositiveInt(String raw, String name) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                throw new InitialStateException(name + " must be a positive integer, was '" + raw + "'");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new InitialStateException(name + " must be a positive integer, was '" + raw + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private List<ZoneCreationDTO> parseZones(Object raw, String action) {
        if (!(raw instanceof List)) {
            throw new InitialStateException("'" + action + "': 'zones' must be a JSON array.");
        }
        List<Map<String, Object>> list = (List<Map<String, Object>>) raw;
        return list.stream().map(z -> {
            String name = String.valueOf(z.get("name"));
            ZoneType type;
            try {
                type = ZoneType.valueOf(String.valueOf(z.get("type")).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InitialStateException("invalid zone type '" + z.get("type") + "' (expected STANDING or SEATED)");
            }
            double basePrice = ((Number) z.get("basePrice")).doubleValue();
            int capacity = ((Number) z.get("capacity")).intValue();
            return new ZoneCreationDTO(name, type, basePrice, capacity);
        }).collect(Collectors.toList());
    }

    private double parseDouble(String raw, String name) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new InitialStateException(name + " must be a number, was '" + raw + "'");
        }
    }

    /** Per-operation helper: argument lookup, binding-name resolution, and result binding. */
    private static final class Ctx {
        private final InitOperation op;
        private final Map<String, String> bindings;

        Ctx(InitOperation op, Map<String, String> bindings) {
            this.op = op;
            this.bindings = bindings;
        }

        String arg(String key) {
            Object raw = op.args().get(key);
            if (raw == null) {
                throw new InitialStateException("missing required argument '" + key + "'.");
            }
            return resolve(String.valueOf(raw));
        }

        String optArg(String key, String fallback) {
            Object raw = op.args().get(key);
            return raw == null ? fallback : resolve(String.valueOf(raw));
        }

        /** Substitute a previously bound name with its value; otherwise return the literal. */
        private String resolve(String value) {
            return bindings.getOrDefault(value, value);
        }

        boolean hasBind() {
            return op.bindTo() != null && !op.bindTo().isBlank();
        }

        void bind(String value) {
            if (hasBind()) {
                bindings.put(op.bindTo(), value);
            }
        }
    }
}
