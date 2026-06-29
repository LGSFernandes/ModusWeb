package com.lfernandes.modusweb.configurations;

import com.lfernandes.modusweb.models.Role;
import com.lfernandes.modusweb.models.User;
import com.lfernandes.modusweb.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Inicializador de dados de desenvolvimento.
 * Cria o usuário ADMIN padrão se ele não existir.
 *
 * ⚠️ Em produção: altere a senha do admin imediatamente após o primeiro login.
 *    Configure ADMIN_EMAIL e ADMIN_PASSWORD via variáveis de ambiente.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager   entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createAdminIfAbsent();
    }

    private void createAdminIfAbsent() {
        String adminEmail = System.getenv().getOrDefault("ADMIN_EMAIL", "admin@modusweb.com");

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin já existe: {}", adminEmail);
            return;
        }

        // Busca as roles via JPQL parametrizado
        Role adminRole  = findRole(Role.ADMIN);
        Role sellerRole = findRole(Role.SELLER);
        Role userRole   = findRole(Role.USER);

        String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "Admin@Modusweb2024!");

        User admin = User.builder()
                .name("Administrador ModusWeb")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .roles(Set.of(adminRole, sellerRole, userRole))
                .enabled(true)
                .build();

        userRepository.save(admin);
        log.warn("✅ Usuário ADMIN criado: {} | Altere a senha em produção!", adminEmail);
    }

    private Role findRole(String roleName) {
        return entityManager
                .createQuery("SELECT r FROM Role r WHERE r.name = :name", Role.class)
                .setParameter("name", roleName)
                .getResultStream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Role não encontrada no banco: " + roleName
                                + " — verifique se a migration V1 rodou corretamente."));
    }
}
