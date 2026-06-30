package com.lfernandes.modusweb.dtos.response;

import com.lfernandes.modusweb.models.Template;
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
public class TemplateSummaryDTO {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private boolean free;
    private String previewImageUrl;
    private String tags;
    private Long downloads;
    private boolean approved;
    private boolean active;
    private Long sellerId;
    private String sellerName;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;

    public static TemplateSummaryDTO from(Template t, String baseUploadUrl) {
        return TemplateSummaryDTO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .price(t.getPrice())
                .free(t.isFree())
                .previewImageUrl(t.getPreviewImage() != null
                        ? baseUploadUrl + "/" + t.getPreviewImage()
                        : null)
                .tags(t.getTags())
                .downloads(t.getDownloads())
                .approved(Boolean.TRUE.equals(t.getApproved()))
                .active(Boolean.TRUE.equals(t.getActive()))
                .sellerId(t.getSeller() != null ? t.getSeller().getId() : null)
                .sellerName(t.getSeller() != null ? t.getSeller().getName() : null)
                .categoryId(t.getCategory() != null ? t.getCategory().getId() : null)
                .categoryName(t.getCategory() != null ? t.getCategory().getName() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
