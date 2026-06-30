package com.lfernandes.modusweb.services;

import com.lfernandes.modusweb.audit.AuditLog;
import com.lfernandes.modusweb.dtos.SellerStatsDTO;
import com.lfernandes.modusweb.exceptions.BusinessException;
import com.lfernandes.modusweb.exceptions.ResourceNotFoundException;
import com.lfernandes.modusweb.models.Order;
import com.lfernandes.modusweb.models.Template;
import com.lfernandes.modusweb.models.User;
import com.lfernandes.modusweb.repositories.OrderRepository;
import com.lfernandes.modusweb.repositories.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository    orderRepository;
    private final TemplateRepository templateRepository;
    private final TemplateService    templateService;
    private final FileStorageService fileStorageService;
    private final AuditLog           auditLog;

    /**
     * Cria um pedido para o template solicitado pelo comprador.
     * Para templates gratuitos, o pedido já entra como COMPLETED.
     * Para templates pagos, será integrado com gateway de pagamento.
     */
    @Transactional
    public Order createOrder(Long templateId, User buyer, String ip) {
        Template template = templateService.findById(templateId);

        if (!Boolean.TRUE.equals(template.getApproved())) {
            throw new BusinessException("Este template não está disponível para compra.");
        }
        if (Boolean.TRUE.equals(buyer.getId().equals(template.getSeller().getId()))) {
            throw new BusinessException("Você não pode comprar seu próprio template.");
        }
        if (orderRepository.existsByBuyerAndTemplate_Id(buyer, templateId)) {
            throw new BusinessException("Você já adquiriu este template.");
        }

        // Para templates gratuitos: completa imediatamente
        Order.Status status = template.isFree()
                ? Order.Status.COMPLETED
                : Order.Status.PENDING; // PENDING = aguarda pagamento externo

        Order order = Order.builder()
                .buyer(buyer)
                .template(template)
                .totalAmount(template.getPrice() != null ? template.getPrice() : BigDecimal.ZERO)
                .status(status)
                .build();

        Order saved = orderRepository.save(order);
        auditLog.log(AuditLog.Action.ORDER_CREATE, buyer.getId(), ip,
                "Pedido #" + saved.getId() + " Template #" + templateId
                        + " status=" + status.name() + " valor=" + saved.getTotalAmount());
        return saved;
    }

    /**
     * Download do template — valida se o comprador possui pedido COMPLETED.
     * Incrementa contador de downloads e retorna o Resource do arquivo.
     */
    @Transactional
    public Resource downloadTemplate(Long templateId, User buyer, String ip) {
        Template template = templateService.findById(templateId);

        // Verifica se o usuário tem um pedido concluído para este template
        boolean hasAccess = template.isFree()
                || orderRepository.existsByBuyerAndTemplate_Id(buyer, templateId);

        if (!hasAccess) {
            throw new BusinessException(
                    "Você precisa adquirir este template antes de baixá-lo.");
        }

        // Incrementa downloads
        templateService.incrementDownloads(templateId);
        auditLog.log(AuditLog.Action.DOWNLOAD_FILE, buyer.getId(), ip,
                "Download Template #" + templateId + " [" + template.getTitle() + "]");

        return fileStorageService.loadAsResource(template.getFilePath());
    }

    /**
     * Completa manualmente um pedido (ex: após confirmação de pagamento).
     * Em produção, este método é chamado pelo webhook do gateway de pagamento.
     */
    @Transactional
    public Order completeOrder(Long orderId, User admin, String ip) {
        Order order = findById(orderId);
        order.setStatus(Order.Status.COMPLETED);
        Order saved = orderRepository.save(order);
        auditLog.log(AuditLog.Action.ORDER_COMPLETE, admin.getId(), ip,
                "Pedido #" + orderId + " marcado como COMPLETED");
        return saved;
    }

    @Transactional
    public Order cancelOrder(Long orderId, User actor, String ip) {
        Order order = findById(orderId);
        if (order.getStatus() == Order.Status.COMPLETED) {
            throw new BusinessException("Pedidos concluídos não podem ser cancelados diretamente.");
        }
        order.setStatus(Order.Status.CANCELLED);
        Order saved = orderRepository.save(order);
        auditLog.log(AuditLog.Action.ORDER_CANCEL, actor.getId(), ip, "Pedido #" + orderId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> findByBuyer(User buyer) {
        return orderRepository.findByBuyerOrderByCreatedAtDesc(buyer);
    }

    @Transactional(readOnly = true)
    public List<Order> findBySeller(User seller) {
        return orderRepository.findByTemplateSellerOrderByCreatedAtDesc(seller);
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido #" + id + " não encontrado."));
    }

    /**
     * Calcula métricas consolidadas de um vendedor para o dashboard.
     */
    @Transactional(readOnly = true)
    public SellerStatsDTO getSellerStats(User seller) {
        List<Template> templates = templateRepository.findBySellerOrderByCreatedAtDesc(seller);

        long totalTemplates    = templates.size();
        long approvedTemplates = templates.stream().filter(t -> Boolean.TRUE.equals(t.getApproved())).count();
        long pendingTemplates  = templates.stream().filter(t -> !Boolean.TRUE.equals(t.getApproved()) && Boolean.TRUE.equals(t.getActive())).count();
        long totalDownloads    = templates.stream().mapToLong(t -> t.getDownloads() != null ? t.getDownloads() : 0L).sum();
        BigDecimal revenue     = orderRepository.sumRevenueBy(seller);
        long totalSales        = orderRepository.findByTemplateSellerOrderByCreatedAtDesc(seller)
                .stream().filter(o -> o.getStatus() == Order.Status.COMPLETED).count();

        return SellerStatsDTO.builder()
                .totalTemplates(totalTemplates)
                .approvedTemplates(approvedTemplates)
                .pendingTemplates(pendingTemplates)
                .totalSales(totalSales)
                .totalDownloads(totalDownloads)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .build();
    }
}
