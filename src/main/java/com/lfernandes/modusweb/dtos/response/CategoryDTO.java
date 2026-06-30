package com.lfernandes.modusweb.dtos.response;

import com.lfernandes.modusweb.models.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String icon;

    public static CategoryDTO from(Category c) {
        return CategoryDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .description(c.getDescription())
                .icon(c.getIcon())
                .build();
    }
}
