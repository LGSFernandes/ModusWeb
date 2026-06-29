package com.lfernandes.modusweb.dtos;

import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SellerStatsDTO {
    private long totalTemplates;
    private long approvedTemplates;
    private long pendingTemplates;
    private long totalSales;
    private long totalDownloads;
    private BigDecimal totalRevenue;
}
