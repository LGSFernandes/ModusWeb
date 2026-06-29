package com.lfernandes.modusweb.repositories;

import com.lfernandes.modusweb.models.Template;
import com.lfernandes.modusweb.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    /** Todos os templates aprovados e ativos (vitrine pública) */
    Page<Template> findByApprovedTrueAndActiveTrue(Pageable pageable);

    /** Busca por título ou tags (vitrine pública) */
    @Query("""
        SELECT t FROM Template t
        WHERE t.approved = true AND t.active = true
          AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.tags) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<Template> searchApproved(@Param("q") String query, Pageable pageable);

    /** Templates por categoria (vitrine pública) */
    Page<Template> findByCategory_SlugAndApprovedTrueAndActiveTrue(String slug, Pageable pageable);

    /** Templates de um vendedor específico */
    List<Template> findBySellerOrderByCreatedAtDesc(User seller);

    /** Templates aguardando aprovação do admin */
    List<Template> findByApprovedFalseAndActiveTrueOrderByCreatedAtAsc();

    /** Contagem de templates aprovados */
    long countByApprovedTrue();

    /** Contagem de templates pendentes */
    long countByApprovedFalseAndActiveTrue();

    /** Top templates mais baixados */
    List<Template> findTop6ByApprovedTrueAndActiveTrueOrderByDownloadsDesc();
}