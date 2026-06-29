package com.lfernandes.modusweb.models;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;

@Entity
@Table(name = "roles")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Role implements GrantedAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Override
    public String getAuthority() {
        return name;
    }

    public static final String USER   = "ROLE_USER";
    public static final String SELLER = "ROLE_SELLER";
    public static final String ADMIN  = "ROLE_ADMIN";
}
