package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.cardtype.CardTypeResponse;
import com.duanttcsn5.library.dto.cardtype.CreateCardTypeRequest;
import com.duanttcsn5.library.dto.cardtype.PolicyHistoryResponse;
import com.duanttcsn5.library.dto.cardtype.UpdateCardTypeRequest;
import com.duanttcsn5.library.entity.CardType;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.CardTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class CardTypeService {

    private final CardTypeRepository cardTypeRepository;
    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbcTemplate;

    public CardTypeService(CardTypeRepository cardTypeRepository,
                           AuditLogRepository auditLogRepository,
                           JdbcTemplate jdbcTemplate) {
        this.cardTypeRepository = cardTypeRepository;
        this.auditLogRepository = auditLogRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<CardTypeResponse> getAllCardTypes() {
        return cardTypeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ct -> {
                    long usageCount = cardTypeRepository.countLibraryCardsUsingType(ct.getId());
                    return CardTypeResponse.fromEntity(ct, usageCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CardTypeResponse> getActiveCardTypes() {
        return cardTypeRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
                .map(ct -> {
                    long usageCount = cardTypeRepository.countLibraryCardsUsingType(ct.getId());
                    return CardTypeResponse.fromEntity(ct, usageCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CardTypeResponse getCardTypeById(Long id) {
        CardType ct = cardTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CARD_TYPE_NOT_FOUND", "Không tìm thấy loại thẻ ID: " + id));
        long usageCount = cardTypeRepository.countLibraryCardsUsingType(ct.getId());
        return CardTypeResponse.fromEntity(ct, usageCount);
    }

    @Transactional
    public CardTypeResponse createCardType(CreateCardTypeRequest request, Long currentUserId, String ipAddress) {
        String trimmedName = request.name().trim();
        if (cardTypeRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "CARD_TYPE_NAME_EXISTS", "Tên loại thẻ '" + trimmedName + "' đã tồn tại trong hệ thống.");
        }

        validatePolicyConstraints(request.maxBooks(), request.loanDays(), request.maxRenewals(), request.renewalDays());

        CardType cardType = new CardType();
        cardType.setName(trimmedName);
        cardType.setDescription(request.description() != null ? request.description().trim() : null);
        cardType.setDurationMonths(request.duration() != null && request.duration() > 0 ? request.duration() : 12);
        cardType.setMaxBooks(request.maxBooks());
        cardType.setLoanDays(request.loanDays());
        cardType.setMaxRenewals(request.maxRenewals());
        cardType.setRenewalDays(request.renewalDays());
        cardType.setActive(true);
        cardType.setCreatedBy(currentUserId);
        cardType.setUpdatedBy(currentUserId);

        CardType saved = cardTypeRepository.save(cardType);

        String afterPolicy = formatPolicy(saved);
        auditLogRepository.insert(
                currentUserId,
                "CARD_TYPE_CREATED",
                "CARD_TYPE",
                saved.getId().toString(),
                "{\"action\":\"Tạo mới loại thẻ\",\"name\":\"" + escapeJson(saved.getName()) + "\",\"policy\":\"" + escapeJson(afterPolicy) + "\"}",
                ipAddress
        );

        return CardTypeResponse.fromEntity(saved, 0);
    }

    @Transactional
    public CardTypeResponse updateCardType(Long id, UpdateCardTypeRequest request, Long currentUserId, String ipAddress) {
        CardType cardType = cardTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CARD_TYPE_NOT_FOUND", "Không tìm thấy loại thẻ ID: " + id));

        String trimmedName = request.name().trim();
        if (cardTypeRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "CARD_TYPE_NAME_EXISTS", "Tên loại thẻ '" + trimmedName + "' đã được sử dụng bởi loại thẻ khác.");
        }

        validatePolicyConstraints(request.maxBooks(), request.loanDays(), request.maxRenewals(), request.renewalDays());

        String beforePolicy = formatPolicy(cardType);

        cardType.setName(trimmedName);
        cardType.setDescription(request.description() != null ? request.description().trim() : null);
        if (request.duration() != null && request.duration() > 0) {
            cardType.setDurationMonths(request.duration());
        }
        cardType.setMaxBooks(request.maxBooks());
        cardType.setLoanDays(request.loanDays());
        cardType.setMaxRenewals(request.maxRenewals());
        cardType.setRenewalDays(request.renewalDays());
        cardType.setUpdatedBy(currentUserId);

        CardType updated = cardTypeRepository.save(cardType);
        String afterPolicy = formatPolicy(updated);

        auditLogRepository.insert(
                currentUserId,
                "CARD_TYPE_UPDATED",
                "CARD_TYPE",
                updated.getId().toString(),
                "{\"action\":\"Cập nhật chính sách\",\"name\":\"" + escapeJson(updated.getName()) + "\",\"before\":\"" + escapeJson(beforePolicy) + "\",\"after\":\"" + escapeJson(afterPolicy) + "\"}",
                ipAddress
        );

        long usageCount = cardTypeRepository.countLibraryCardsUsingType(id);
        return CardTypeResponse.fromEntity(updated, usageCount);
    }

    @Transactional
    public CardTypeResponse toggleStatus(Long id, Long currentUserId, String ipAddress) {
        CardType cardType = cardTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CARD_TYPE_NOT_FOUND", "Không tìm thấy loại thẻ ID: " + id));

        String beforePolicy = formatPolicy(cardType);
        cardType.setActive(!cardType.isActive());
        cardType.setUpdatedBy(currentUserId);

        CardType saved = cardTypeRepository.save(cardType);
        String afterPolicy = formatPolicy(saved);
        String actionName = saved.isActive() ? "Áp dụng lại" : "Ngừng áp dụng";

        auditLogRepository.insert(
                currentUserId,
                saved.isActive() ? "CARD_TYPE_ACTIVATED" : "CARD_TYPE_DEACTIVATED",
                "CARD_TYPE",
                saved.getId().toString(),
                "{\"action\":\"" + actionName + "\",\"name\":\"" + escapeJson(saved.getName()) + "\",\"before\":\"" + escapeJson(beforePolicy) + "\",\"after\":\"" + escapeJson(afterPolicy) + "\"}",
                ipAddress
        );

        long usageCount = cardTypeRepository.countLibraryCardsUsingType(id);
        return CardTypeResponse.fromEntity(saved, usageCount);
    }

    @Transactional
    public void deleteCardType(Long id, Long currentUserId, String ipAddress) {
        CardType cardType = cardTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CARD_TYPE_NOT_FOUND", "Không tìm thấy loại thẻ ID: " + id));

        long usageCount = cardTypeRepository.countLibraryCardsUsingType(id);
        if (usageCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CARD_TYPE_IN_USE",
                    "Không thể xoá loại thẻ '" + cardType.getName() + "' vì đang có " + usageCount + " thẻ bạn đọc sử dụng. Vui lòng chọn ngừng áp dụng.");
        }

        String beforePolicy = formatPolicy(cardType);
        cardTypeRepository.delete(cardType);

        auditLogRepository.insert(
                currentUserId,
                "CARD_TYPE_DELETED",
                "CARD_TYPE",
                id.toString(),
                "{\"action\":\"Xoá loại thẻ\",\"name\":\"" + escapeJson(cardType.getName()) + "\",\"before\":\"" + escapeJson(beforePolicy) + "\"}",
                ipAddress
        );
    }

    @Transactional(readOnly = true)
    public List<PolicyHistoryResponse> getPolicyHistory() {
        String sql = """
                SELECT
                    al.id,
                    al.action,
                    COALESCE(al.after_data->>'name', al.entity_id) AS card_type_name,
                    COALESCE(u.full_name, 'Hệ thống') AS changed_by,
                    al.created_at,
                    COALESCE(al.after_data->>'before', '-') AS before_policy,
                    COALESCE(al.after_data->>'after', al.after_data->>'policy', '-') AS after_policy
                FROM audit_logs al
                LEFT JOIN users u ON al.actor_user_id = u.id
                WHERE al.entity_type = 'CARD_TYPE'
                ORDER BY al.created_at DESC
                LIMIT 50
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String rawAction = rs.getString("action");
            String displayAction = switch (rawAction) {
                case "CARD_TYPE_CREATED" -> "Tạo mới loại thẻ";
                case "CARD_TYPE_UPDATED" -> "Cập nhật chính sách";
                case "CARD_TYPE_ACTIVATED" -> "Áp dụng lại";
                case "CARD_TYPE_DEACTIVATED" -> "Ngừng áp dụng";
                case "CARD_TYPE_DELETED" -> "Xoá loại thẻ";
                default -> rawAction;
            };

            return new PolicyHistoryResponse(
                    rs.getLong("id"),
                    rs.getString("card_type_name"),
                    displayAction,
                    rs.getString("changed_by"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getString("before_policy"),
                    rs.getString("after_policy")
            );
        });
    }

    private void validatePolicyConstraints(int maxBooks, int loanDays, int maxRenewals, int renewalDays) {
        if (maxBooks <= 0 || maxBooks > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MAX_BOOKS", "Số sách tối đa được mượn cùng lúc phải từ 1 đến 10 cuốn.");
        }
        if (loanDays <= 0 || loanDays > 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOAN_DAYS", "Số ngày mượn phải từ 1 đến 60 ngày.");
        }
        if (maxRenewals <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MAX_RENEWALS", "Số lần gia hạn tối đa phải lớn hơn 0.");
        }
        if (renewalDays <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RENEWAL_DAYS", "Số ngày mỗi lần gia hạn phải lớn hơn 0.");
        }
    }

    private String formatPolicy(CardType ct) {
        return ct.getMaxBooks() + " sách • " +
                ct.getLoanDays() + " ngày mượn • " +
                ct.getMaxRenewals() + " lần gia hạn • " +
                ct.getRenewalDays() + " ngày/lần • " +
                ct.getDurationMonths() + " tháng • " +
                (ct.isActive() ? "Đang áp dụng" : "Ngừng áp dụng");
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
