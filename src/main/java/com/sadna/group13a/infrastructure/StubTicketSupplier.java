package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Interfaces.ITicketSupplier;
import com.sadna.group13a.application.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class StubTicketSupplier implements ITicketSupplier {

    private static final Logger logger = LoggerFactory.getLogger(StubTicketSupplier.class);

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Result<List<String>> issueTickets(String orderId, int quantity) {
        List<String> codes = IntStream.range(0, quantity)
                .mapToObj(i -> "TKT-" + UUID.randomUUID())
                .collect(Collectors.toList());
        logger.info("[TICKET] Stub issued {} ticket(s) for order {}.", quantity, orderId);
        return Result.success(codes);
    }

    @Override
    public Result<Void> cancelTickets(List<String> ticketCodes) {
        logger.info("[TICKET] Stub cancelled {} ticket(s).", ticketCodes.size());
        return Result.success();
    }
}
