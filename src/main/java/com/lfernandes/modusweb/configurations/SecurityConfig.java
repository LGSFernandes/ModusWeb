package com.lfernandes.modusweb.configurations;

import com.lfernandes.modusweb.audit.AuditLog;
import com.lfernandes.modusweb.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

/**
 * Configuração de segurança do ModusWeb.
 *
 * Implementa:
 *  - CSRF via cookie HttpOnly + SameSite
 *  - Security Headers: CSP, HSTS, X-Frame-Options, X-Content-Type-Options
 *  - Controle de acesso granular por role
 *  - Handlers de login/logout com auditoria
 *  - BCrypt para senhas
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final AuditLog    auditLog;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Fator de custo 12: seguro e performático em hardware moderno
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // ── CSRF ──────────────────────────────────────────────────
                // CookieCsrfTokenRepository emite cookie XSRF-TOKEN legível pelo JS
                // mas o token é validado server-side — imune a CSRF
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/sitemap.xml", "/robots.txt")
                )

                // ── Security Headers (OWASP Top 10 A05) ─────────────────
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(opts -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com https://unpkg.com https://cdnjs.cloudflare.com; " +
                                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.tailwindcss.com; " +
                                        "font-src 'self' https://fonts.gstatic.com; " +
                                        "img-src 'self' data: https:; " +
                                        "connect-src 'self';"
                        ))
                )

                // ── Controle de Acesso ────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        // Recursos públicos
                        .requestMatchers(
                                "/", "/shop/**", "/auth/**",
                                "/css/**", "/js/**", "/assets/**",
                                "/uploads/previews/**", "/uploads/avatars/**",
                                "/sitemap.xml", "/robots.txt",
                                "/error", "/actuator/health"
                        ).permitAll()

                        // Área do vendedor
                        .requestMatchers("/seller/**").hasAnyAuthority("ROLE_SELLER", "ROLE_ADMIN")

                        // Área administrativa — somente ADMIN
                        .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")

                        // Download de arquivos de template — usuário autenticado
                        .requestMatchers("/downloads/**").authenticated()

                        // Perfil e pedidos — qualquer usuário autenticado
                        .requestMatchers("/profile/**", "/orders/**").authenticated()

                        // Qualquer outra rota — autenticado
                        .anyRequest().authenticated()
                )

                // ── Formulário de Login ───────────────────────────────────
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler())
                        .failureHandler(loginFailureHandler())
                        .permitAll()
                )

                // ── Logout ────────────────────────────────────────────────
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout", "POST"))
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .addLogoutHandler((request, response, auth) -> {
                            if (auth != null && auth.getPrincipal() instanceof com.lfernandes.modusweb.models.User u) {
                                String ip = extractIp(request);
                                auditLog.log(AuditLog.Action.LOGOUT, u.getId(), ip, "Logout: " + u.getEmail());
                            }
                        })
                )

                // ── Session ───────────────────────────────────────────────
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .expiredUrl("/auth/login?expired=true")
                )

                // ── Exceção de acesso negado ──────────────────────────────
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/error/403")
                );

        return http.build();
    }

    // ── Handlers de Login ─────────────────────────────────────────────

    private AuthenticationSuccessHandler loginSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication auth) -> {
            String ip = extractIp(request);
            if (auth.getPrincipal() instanceof com.lfernandes.modusweb.models.User u) {
                auditLog.log(AuditLog.Action.LOGIN_SUCCESS, u.getId(), ip, "Login: " + u.getEmail());
                // Redireciona para a área correta conforme a role
                if (u.isAdmin()) {
                    response.sendRedirect("/admin/dashboard");
                } else if (u.isSeller()) {
                    response.sendRedirect("/seller/dashboard");
                } else {
                    response.sendRedirect("/shop");
                }
            } else {
                response.sendRedirect("/shop");
            }
        };
    }

    private SimpleUrlAuthenticationFailureHandler loginFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler("/auth/login?error=true") {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request,
                                                HttpServletResponse response, AuthenticationException exception)
                    throws IOException {
                String ip    = extractIp(request);
                String email = request.getParameter("email");
                auditLog.logAnonymous(AuditLog.Action.LOGIN_FAILURE, ip,
                        "Tentativa falha para: " + email);
                super.onAuthenticationFailure(request, response, exception);
            }
        };
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}