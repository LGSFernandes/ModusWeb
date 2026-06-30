package com.lfernandes.modusweb.services;

import com.lfernandes.modusweb.audit.AuditLog;
import com.lfernandes.modusweb.dtos.TemplateUploadDTO;
import com.lfernandes.modusweb.exceptions.BusinessException;
import com.lfernandes.modusweb.exceptions.ResourceNotFoundException;
import com.lfernandes.modusweb.models.Category;
import com.lfernandes.modusweb.models.Template;
import com.lfernandes.modusweb.models.User;
import com.lfernandes.modusweb.repositories.CategoryRepository;
import com.lfernandes.modusweb.repositories.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final ContentModerationService moderationService;
    private final AuditLog auditLog;

    // ── Vitrine pública ──────────────────────────────────────────────

    @Cacheable(value = "templates-vitrine", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<Template> listApproved(Pageable pageable) {
        return templateRepository.findByApprovedTrueAndActiveTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Template> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) return listApproved(pageable);
        return templateRepository.searchApproved(query.trim(), pageable);
    }

    @Cacheable(value = "templates-vitrine", key = "'cat-' + #slug + '-' + #pageable.pageNumber")
    @Transactional(readOnly = true)
    public Page<Template> listByCategory(String slug, Pageable pageable) {
        return templateRepository.findByCategory_SlugAndApprovedTrueAndActiveTrue(slug, pageable);
    }

    @Cacheable(value = "template-detalhe", key = "#id")
    @Transactional(readOnly = true)
    public Template findById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template #" + id + " não encontrado."));
    }

    @Cacheable("top-downloads")
    @Transactional(readOnly = true)
    public List<Template> topDownloads() {
        return templateRepository.findTop6ByApprovedTrueAndActiveTrueOrderByDownloadsDesc();
    }

    @Cacheable("categorias")
    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryRepository.findAll();
    }

    // ── Seller ───────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = {"templates-vitrine", "top-downloads"}, allEntries = true)
    public Template upload(TemplateUploadDTO dto, User seller, String ip) {
        // 1. Moderação de conteúdo (texto)
        moderationService.moderateText(dto.getTitle(), dto.getDescription(), seller.getId(), ip);
        moderationService.moderateField("tags", dto.getTags(), seller.getId(), ip);

        // 2. Moderação de arquivos
        if (dto.getTemplateFile() != null && !dto.getTemplateFile().isEmpty()) {
            moderationService.moderateTemplateFile(dto.getTemplateFile(), seller.getId(), ip);
        } else {
            throw new BusinessException("O arquivo do template é obrigatório.");
        }

        // 3. Armazenamento
        String filePath = fileStorageService.storeTemplateFile(dto.getTemplateFile());
        String previewPath = null;
        if (dto.getPreviewImage() != null && !dto.getPreviewImage().isEmpty()) {
            moderationService.moderateImage(dto.getPreviewImage(), seller.getId(), ip);
            previewPath = fileStorageService.storePreviewImage(dto.getPreviewImage());
        }

        // 4. Resolução da categoria
        Category category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
        }

        // 5. Persistência — template fica pendente de aprovação
        Template template = Template.builder()
                .title(dto.getTitle().trim())
                .description(dto.getDescription().trim())
                .price(dto.getPrice())
                .tags(dto.getTags())
                .filePath(filePath)
                .previewImage(previewPath)
                .category(category)
                .seller(seller)
                .approved(false)
                .active(true)
                .build();

        Template saved = templateRepository.save(template);
        auditLog.log(AuditLog.Action.TEMPLATE_UPLOAD, seller.getId(), ip,
                "Template #" + saved.getId() + " [" + saved.getTitle() + "] enviado para revisão.");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Template> findBySeller(User seller) {
        return templateRepository.findBySellerOrderByCreatedAtDesc(seller);
    }

    // ── Admin ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Template> listPending() {
        return templateRepository.findByApprovedFalseAndActiveTrueOrderByCreatedAtAsc();
    }

    @Transactional
    @CacheEvict(value = {"templates-vitrine", "template-detalhe", "top-downloads"}, allEntries = true)
    public void approve(Long templateId, User admin, String ip) {
        Template t = findById(templateId);
        t.setApproved(true);
        templateRepository.save(t);
        auditLog.log(AuditLog.Action.TEMPLATE_APPROVE, admin.getId(), ip,
                "Template #" + templateId + " aprovado por " + admin.getEmail());
    }

    @Transactional
    @CacheEvict(value = {"templates-vitrine", "template-detalhe", "top-downloads"}, allEntries = true)
    public void reject(Long templateId, User admin, String ip) {
        Template t = findById(templateId);
        t.setApproved(false);
        t.setActive(false);
        templateRepository.save(t);
        auditLog.log(AuditLog.Action.TEMPLATE_REJECT, admin.getId(), ip,
                "Template #" + templateId + " rejeitado por " + admin.getEmail());
    }

    @Transactional
    @CacheEvict(value = {"templates-vitrine", "template-detalhe", "top-downloads"}, allEntries = true)
    public void delete(Long templateId, User actor, String ip) {
        Template t = findById(templateId);
        // Apenas o próprio seller ou admin pode deletar
        boolean isSeller = t.getSeller().getId().equals(actor.getId());
        boolean isAdmin  = actor.isAdmin();
        if (!isSeller && !isAdmin) {
            throw new BusinessException("Você não tem permissão para deletar este template.");
        }
        fileStorageService.delete(t.getFilePath());
        fileStorageService.delete(t.getPreviewImage());
        templateRepository.delete(t);
        auditLog.log(AuditLog.Action.TEMPLATE_DELETE, actor.getId(), ip,
                "Template #" + templateId + " deletado.");
    }

    @Transactional
    public void incrementDownloads(Long templateId) {
        Template t = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template não encontrado."));
        t.incrementDownloads();
        templateRepository.save(t);
    }
}