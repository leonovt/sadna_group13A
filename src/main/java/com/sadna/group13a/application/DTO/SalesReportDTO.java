package com.sadna.group13a.application.DTO;

import java.util.List;

public record SalesReportDTO(
    String companyId,
    String companyName,
    int totalOrders,
    double totalRevenue,
    List<OrderHistoryDTO> orders
) {}
