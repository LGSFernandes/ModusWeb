package com.lfernandes.modusweb.dtos.response;

import com.lfernandes.modusweb.models.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDTO {
    private Long id;
    private String name;
    private String email;
    private String bio;
    private String avatarUrl;
    private boolean enabled;
    private List<String> roles;
    private LocalDateTime createdAt;

    public static UserSummaryDTO from(User user) {
        return UserSummaryDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .enabled(user.isEnabled())
                .roles(user.getRoles().stream()
                        .map(r -> r.getName())
                        .collect(Collectors.toList()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
