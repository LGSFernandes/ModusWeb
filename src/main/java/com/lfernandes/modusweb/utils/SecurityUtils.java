package com.lfernandes.modusweb.utils;

import com.lfernandes.modusweb.models.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Utilitário de segurança para acesso ao usuário autenticado */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) return Optional.of(user);
        return Optional.empty();
    }

    public static User currentUserOrThrow() {
        return currentUser().orElseThrow(() ->
                new IllegalStateException("Nenhum usuário autenticado na sessão"));
    }
}
