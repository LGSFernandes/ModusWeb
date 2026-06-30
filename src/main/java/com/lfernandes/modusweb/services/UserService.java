package com.lfernandes.modusweb.services;

import com.lfernandes.modusweb.audit.AuditLog;
import com.lfernandes.modusweb.dtos.RegisterDTO;
import com.lfernandes.modusweb.dtos.UserProfileDTO;
import com.lfernandes.modusweb.exceptions.BusinessException;
import com.lfernandes.modusweb.exceptions.ResourceNotFoundException;
import com.lfernandes.modusweb.models.Role;
import com.lfernandes.modusweb.models.User;
import com.lfernandes.modusweb.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContentModerationService moderationService;
    private final FileStorageService fileStorageService;
    private final AuditLog auditLog;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));
    }

    @Transactional
    public User register(RegisterDTO dto, String ip) {
        if (userRepository.existsByEmail(dto.getEmail().trim().toLowerCase())) {
            throw new BusinessException("Este e-mail já está cadastrado.");
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException("As senhas não coincidem.");
        }
        moderationService.moderateField("name", dto.getName(), null, ip);

        Role userRole = findRole(Role.USER);
        Set<Role> roles = new HashSet<>(List.of(userRole));
        if (dto.isRegisterAsSeller()) {
            roles.add(findRole(Role.SELLER));
        }

        User user = User.builder()
                .name(dto.getName().trim())
                .email(dto.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(dto.getPassword()))
                .roles(roles)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        auditLog.log(AuditLog.Action.REGISTER, saved.getId(), ip, "Registro: " + saved.getEmail());
        return saved;
    }

    @Transactional
    public User updateProfile(User currentUser, UserProfileDTO dto, String ip) {
        moderationService.moderateField("bio", dto.getBio(), currentUser.getId(), ip);
        currentUser.setName(dto.getName().trim());
        currentUser.setBio(dto.getBio() != null ? dto.getBio().trim() : null);

        if (dto.getNewPassword() != null && !dto.getNewPassword().isBlank()) {
            if (!dto.getNewPassword().equals(dto.getConfirmNewPassword()))
                throw new BusinessException("As novas senhas não coincidem.");
            if (dto.getNewPassword().length() < 8)
                throw new BusinessException("Senha deve ter mínimo 8 caracteres.");
            currentUser.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        if (dto.getAvatar() != null && !dto.getAvatar().isEmpty()) {
            moderationService.moderateImage(dto.getAvatar(), currentUser.getId(), ip);
            fileStorageService.delete(currentUser.getAvatarUrl());
            currentUser.setAvatarUrl(fileStorageService.storeAvatar(dto.getAvatar()));
        }

        User updated = userRepository.save(currentUser);
        auditLog.log(AuditLog.Action.PROFILE_UPDATE, updated.getId(), ip, updated.getEmail());
        return updated;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() { return userRepository.findAll(); }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário #" + id + " não encontrado"));
    }

    @Transactional
    public void toggleEnabled(Long userId, User admin, String ip) {
        User target = findById(userId);
        target.setEnabled(!target.isEnabled());
        userRepository.save(target);
        auditLog.log(target.isEnabled() ? AuditLog.Action.ADMIN_UNBAN_USER : AuditLog.Action.ADMIN_BAN_USER,
                admin.getId(), ip, "Target: " + target.getEmail());
    }

    private Role findRole(String roleName) {
        return entityManager.createQuery("SELECT r FROM Role r WHERE r.name = :name", Role.class)
                .setParameter("name", roleName)
                .getResultStream().findFirst()
                .orElseThrow(() -> new BusinessException("Role não encontrada: " + roleName));
    }
}
