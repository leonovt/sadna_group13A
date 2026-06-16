package com.sadna.group13a.infrastructure.initstate;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.EventService;
import com.sadna.group13a.application.Services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * </ul>
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

    public InitialStateExecutor(UserService userService,
                                CompanyService companyService,
                                EventService eventService) {
        this.userService = userService;
        this.companyService = companyService;
        this.eventService = eventService;
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
