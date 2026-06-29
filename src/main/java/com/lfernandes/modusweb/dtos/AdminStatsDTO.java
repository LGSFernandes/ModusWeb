package com.lfernandes.modusweb.dtos;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AdminStatsDTO {
    private long totalUsers;
    private long totalSellers;
    private long totalTemplates;
    private long pendingTemplates;
    private long totalOrders;
    private BigDecimal totalRevenue;
}