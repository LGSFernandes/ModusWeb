package com.lfernandes.modusweb.repositories;

import com.lfernandes.modusweb.models.Order;
import com.lfernandes.modusweb.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Todos os pedidos de um comprador */
    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);

    /** Verifica se um comprador já adquiriu um template */
    boolean existsByBuyerAndTemplate_Id(User buyer, Long templateId);

    /** Receita total de um vendedor (pedidos completados) */
    @Query("""
        SELECT COALESCE(SUM(o.totalAmount), 0)
        FROM Order o
        WHERE o.template.seller = :seller
          AND o.status = 'COMPLETED'
        """)
    BigDecimal sumRevenueBy(@Param("seller") User seller);

    /** Pedidos de um vendedor (via template.seller) */
    @Query("SELECT o FROM Order o WHERE o.template.seller = :seller ORDER BY o.createdAt DESC")
    List<Order> findByTemplateSellerOrderByCreatedAtDesc(@Param("seller") User seller);

    /** Contagem total de pedidos completos */
    long countByStatus(Order.Status status);

    /** Receita total da plataforma */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = 'COMPLETED'")
    BigDecimal totalRevenue();

    Optional<Order> findByBuyerAndTemplate_Id(User buyer, Long templateId);
}