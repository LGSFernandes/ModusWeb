package com.lfernandes.modusweb.dtos.response;

import com.lfernandes.modusweb.models.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {
    private Long id;
    private String status;
    private BigDecimal totalAmount;
    private Long templateId;
    private String templateTitle;
    private Long buyerId;
    private String buyerName;
    private LocalDateTime createdAt;

    public static OrderSummaryDTO from(Order o) {
        return OrderSummaryDTO.builder()
                .id(o.getId())
                .status(o.getStatus().name())
                .totalAmount(o.getTotalAmount())
                .templateId(o.getTemplate() != null ? o.getTemplate().getId() : null)
                .templateTitle(o.getTemplate() != null ? o.getTemplate().getTitle() : null)
                .buyerId(o.getBuyer() != null ? o.getBuyer().getId() : null)
                .buyerName(o.getBuyer() != null ? o.getBuyer().getName() : null)
                .createdAt(o.getCreatedAt())
                .build();
    }
}
