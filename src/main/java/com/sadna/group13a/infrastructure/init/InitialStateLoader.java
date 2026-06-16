package com.sadna.group13a.infrastructure.init;

import com.sadna.group13a.application.Result;
import com.sadna.group13a.application.Services.CompanyService;
import com.sadna.group13a.application.Services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes an optional initial-state file at start-up (issue #230): a sequence of legal
 * operations that seeds the system into a known state.
 *
 * <p>Semantics:
 * <ul>
 *   <li>A blank path or empty file → clean start (no error).</li>
 *   <li>A malformed line (unknown command, wrong arity, unterminated quote) → parse error.</li>
 *   <li>A failed or illegal operation (e.g. creating a company without logging in) →
 *       the whole initialization fails with a {@link SystemInitializationException}.</li>
 * </ul>
 *
 * <p>Supported commands (whitespace-separated, double quotes group arguments):
 * <pre>
 *   register &lt;username&gt; &lt;password&gt;
 *   login &lt;username&gt; &lt;password&gt;
 *   logout &lt;username&gt;
 *   create-company &lt;username&gt; &lt;name&gt; [description]
 * </pre>
 * Lines starting with {@code #} and blank lines are ignored.
 */
@Component("textInitialStateLoader")
public class InitialStateLoader {

    private static final Logger logger = LoggerFactory.getLogger(InitialStateLoader.class);

    private final UserService userService;
    private final CompanyService companyService;

    private final Map<String, String> tokensByUser = new HashMap<>();

    public InitialStateLoader(UserService userService, CompanyService companyService) {
        this.userService = userService;
        this.companyService = companyService;
    }

    public void runFromFile(String path) {
        if (path == null || path.isBlank()) {
            logger.info("No initial-state file configured — starting with a clean state.");
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            throw new SystemInitializationException("Cannot read initial-state file '" + path + "': " + e.getMessage(), e);
        }
        execute(lines);
        logger.info("Initial-state file '{}' executed successfully.", path);
    }

    public void execute(List<String> lines) {
        tokensByUser.clear();
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            dispatch(tokenize(line, lineNo), lineNo);
        }
    }

    private void dispatch(List<String> tokens, int lineNo) {
        String command = tokens.get(0).toLowerCase();
        switch (command) {
            case "register" -> {
                requireArity(tokens, 3, lineNo, "register <username> <password>");
                expectSuccess(userService.register(tokens.get(1), tokens.get(2)), lineNo, "register");
            }
            case "login" -> {
                requireArity(tokens, 3, lineNo, "login <username> <password>");
                Result<String> result = userService.login(tokens.get(1), tokens.get(2));
                expectSuccess(result, lineNo, "login");
                tokensByUser.put(tokens.get(1), result.getOrThrow());
            }
            case "logout" -> {
                requireArity(tokens, 2, lineNo, "logout <username>");
                String token = tokensByUser.get(tokens.get(1));
                if (token == null) {
                    throw illegal(lineNo, "'" + tokens.get(1) + "' is not logged in");
                }
                userService.logout(token);
                tokensByUser.remove(tokens.get(1));
            }
            case "create-company" -> {
                requireArity(tokens, 3, 4, lineNo, "create-company <username> <name> [description]");
                String owner = tokens.get(1);
                String token = tokensByUser.get(owner);
                if (token == null) {
                    throw illegal(lineNo, "cannot create a company because '" + owner + "' is not logged in");
                }
                String description = tokens.size() == 4 ? tokens.get(3) : "";
                expectSuccess(companyService.createCompany(token, tokens.get(2), description), lineNo, "create-company");
            }
            default -> throw new SystemInitializationException(
                    "Parse error at line " + lineNo + ": unknown command '" + tokens.get(0) + "'.");
        }
    }

    private void expectSuccess(Result<?> result, int lineNo, String command) {
        if (!result.isSuccess()) {
            throw new SystemInitializationException(
                    "Operation failed at line " + lineNo + " (" + command + "): " + result.getErrorMessage());
        }
    }

    private SystemInitializationException illegal(int lineNo, String message) {
        return new SystemInitializationException("Illegal operation at line " + lineNo + ": " + message + ".");
    }

    private void requireArity(List<String> tokens, int exact, int lineNo, String usage) {
        requireArity(tokens, exact, exact, lineNo, usage);
    }

    private void requireArity(List<String> tokens, int min, int max, int lineNo, String usage) {
        if (tokens.size() < min || tokens.size() > max) {
            throw new SystemInitializationException(
                    "Parse error at line " + lineNo + ": expected usage '" + usage + "'.");
        }
    }

    private List<String> tokenize(String line, int lineNo) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                hasToken = true;
            } else if (Character.isWhitespace(ch) && !inQuotes) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(ch);
                hasToken = true;
            }
        }
        if (inQuotes) {
            throw new SystemInitializationException("Parse error at line " + lineNo + ": unterminated quote.");
        }
        if (hasToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
