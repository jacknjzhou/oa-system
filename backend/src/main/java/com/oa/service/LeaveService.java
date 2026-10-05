package com.oa.service;

import com.oa.dto.LeaveTypeRequest;
import com.oa.entity.LeaveBalance;
import com.oa.entity.LeaveTransaction;
import com.oa.entity.LeaveType;
import com.oa.entity.User;
import com.oa.repository.LeaveBalanceRepository;
import com.oa.repository.LeaveTransactionRepository;
import com.oa.repository.LeaveTypeRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假期服务（AT-03 三账本 + HD-01~07）：
 * 余额账（leave_balance.quota/used/frozen，BigDecimal）+ 发生账（leave_transaction，含操作人/类型/备注）
 * + 额度账（leave_type.annual_quota/unit）。单位间不换算（规格 5.5-⑤）。
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTransactionRepository leaveTransactionRepository;
    private final UserRepository userRepository;

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTypes() {
        return leaveTypeRepository.findByEnabledTrueOrderByWeightDescIdAsc().stream()
                .map(this::toTypeMap)
                .toList();
    }

    // ==================== 类型管理（HD-04~06） ====================

    /** 管理页列表（含停用类型）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminListTypes() {
        return leaveTypeRepository.findAllByOrderByWeightDescIdAsc().stream()
                .map(this::toTypeMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> createType(LeaveTypeRequest req) {
        String code = (req.code() == null || req.code().isBlank())
                ? req.name().trim() : req.code().trim().toUpperCase();
        if (!code.matches("[A-Z0-9_\\u4e00-\\u9fa5]{1,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "代码仅可包含大写字母/数字/下划线/中文（≤32）: " + code);
        }
        if (leaveTypeRepository.findByCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "假期类型已存在: " + code);
        }
        LeaveType type = new LeaveType();
        type.setCode(code);
        applyRequest(type, req);
        leaveTypeRepository.save(type);
        return toTypeMap(type);
    }

    @Transactional
    public Map<String, Object> updateType(Long id, LeaveTypeRequest req) {
        LeaveType type = requireTypeEntity(id);
        if (req.code() != null && !req.code().isBlank()) {
            String code = req.code().trim().toUpperCase();
            if (!code.matches("[A-Z0-9_\\u4e00-\\u9fa5]{1,32}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "代码仅可包含大写字母/数字/下划线/中文（≤32）: " + code);
            }
            leaveTypeRepository.findByCode(code).ifPresent(t -> {
                if (!t.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "假期类型已存在: " + code);
                }
            });
            type.setCode(code);
        }
        applyRequest(type, req);
        leaveTypeRepository.save(type);
        return toTypeMap(type);
    }

    /** 删除：有余额/流水引用的类型禁止删除（引导停用）。 */
    @Transactional
    public void deleteType(Long id) {
        LeaveType type = requireTypeEntity(id);
        long refs = leaveBalanceRepository.countByLeaveTypeId(id);
        long txnRefs = leaveTransactionRepository.countByLeaveTypeId(id);
        if (refs > 0 || txnRefs > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "该类型已有余额/流水记录，不能删除（可停用）");
        }
        leaveTypeRepository.delete(type);
    }

    private LeaveType requireTypeEntity(Long id) {
        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "假期类型不存在"));
    }

    private void applyRequest(LeaveType type, LeaveTypeRequest req) {
        type.setName(req.name().trim());
        type.setCategory(req.category() == null || req.category().isBlank() ? "other" : req.category().trim());
        String quotaType = req.quotaType() == null || req.quotaType().isBlank() ? "none" : req.quotaType().trim();
        if (!List.of("fixed", "accrual", "none").contains(quotaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "额度类型仅支持 fixed/accrual/none");
        }
        type.setQuotaType(quotaType);
        type.setAnnualQuota(req.annualQuota() == null ? 0 : req.annualQuota());
        String unit = req.unit().trim();
        if (!List.of("day", "hour", "half_day").contains(unit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单位仅支持 day/hour/half_day");
        }
        type.setUnit(unit);
        type.setWeight(req.weight() == null ? 100 : req.weight());
        type.setEnabled(req.enabled() == null ? Boolean.TRUE : req.enabled());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> balance(Long userId, String typeCode) {
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        return toBalanceMap(balance, type);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ledger(Long userId, String typeCode) {
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        Map<String, Object> row = toBalanceMap(balance, type);
        List<LeaveTransaction> txns = leaveTransactionRepository
                .findTop50ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(userId, type.getId());
        row.put("transactions", txns.stream().map(t -> toTxnMap(t, type)).toList());
        return List.of(row);
    }

    /** 管理端余额分页（HD-01）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminBalances(Long userId, String keyword, String typeCode, int page, int size) {
        List<User> users = keyword == null || keyword.isBlank()
                ? List.of()
                : userRepository.findAll().stream()
                        .filter(u -> matchesKeyword(u, keyword))
                        .toList();
        Long typeId = typeCode == null || typeCode.isBlank() ? null : requireType(typeCode).getId();

        List<Long> userIds = users.stream().map(User::getId).toList();
        List<Long> typeIds = typeId == null
                ? leaveTypeRepository.findAll().stream().map(LeaveType::getId).toList()
                : List.of(typeId);
        Map<Long, LeaveType> typeById = leaveTypeRepository.findAllById(typeIds).stream()
                .collect(java.util.stream.Collectors.toMap(LeaveType::getId, t -> t));
        Map<Long, LeaveBalance> balanceByPair = new java.util.HashMap<>();
        if (!userIds.isEmpty() && !typeIds.isEmpty()) {
            for (LeaveBalance b : leaveBalanceRepository.findByUserIdInAndLeaveTypeIdIn(userIds, typeIds)) {
                balanceByPair.put(b.getUserId() * 1_000_000L + b.getLeaveTypeId(), b);
            }
        }
        if (users.isEmpty()) {
            // 关键字命中为空
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : users) {
            for (LeaveType type : typeById.values()) {
                LeaveBalance b = balanceByPair.get(u.getId() * 1_000_000L + type.getId());
                if (b == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", u.getId());
                m.put("userName", u.getRealName());
                m.put("userCode", u.getUsername());
                m.put("orgId", u.getOrg() == null ? null : u.getOrg().getId());
                m.put("orgName", u.getOrg() == null ? null : u.getOrg().getOrgName());
                m.put("typeCode", type.getCode());
                m.put("typeName", type.getName());
                m.put("unit", type.getUnit());
                m.put("quota", nvl(b.getQuota()));
                m.put("used", nvl(b.getUsed()));
                m.put("frozen", nvl(b.getFrozen()));
                m.put("available", b.available());
                rows.add(m);
            }
        }
        int from = Math.max(0, page * size);
        if (from >= rows.size()) {
            return List.of();
        }
        return rows.subList(from, Math.min(rows.size(), from + size));
    }

    /** 管理端日志分页（HD-03/08）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> adminLedger(Long userId, String typeCode, String txnType,
                                           String ref, int page, int size) {
        Long typeId = typeCode == null || typeCode.isBlank() ? null : requireType(typeCode).getId();
        Page<LeaveTransaction> result = leaveTransactionRepository.findLog(
                userId, typeId, txnType == null || txnType.isBlank() ? null : txnType,
                ref == null || ref.isBlank() ? null : ref,
                PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, LeaveType> typeById = leaveTypeRepository.findAllById(
                        result.getContent().stream().map(LeaveTransaction::getLeaveTypeId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(LeaveType::getId, t -> t));
        Map<Long, User> userById = userRepository.findAllById(
                        result.getContent().stream()
                                .flatMap(t -> java.util.stream.Stream.of(t.getUserId(), t.getOperatorId()))
                                .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", result.getTotalElements());
        out.put("rows", result.getContent().stream()
                .map(t -> toTxnMap(t, typeById.get(t.getLeaveTypeId()), userById)).toList());
        return out;
    }

    // ==================== 变动 ====================

    /**
     * 授予/调整额度（quota 增量 + 流水）。ref 非空时幂等：同 ref 已授则跳过。
     * operatorId = 操作人（管理端；null = 用户自己/系统）。
     */
    @Transactional
    public Map<String, Object> grant(Long userId, String typeCode, BigDecimal amount,
                                     String reason, String ref, Long operatorId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调整数量必须大于 0");
        }
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        if (ref != null && !ref.isBlank() && hasRef(userId, type.getId(), ref)) {
            return toBalanceMap(balance, type);
        }
        balance.setQuota(nvl(balance.getQuota()).add(amount));
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, amount, reason, ref, "GRANT", operatorId, null);
        return toBalanceMap(balance, type);
    }

    /**
     * 扣减（请假审批通过后调用）。ref 幂等：同一实例重复完成不重复扣。
     * 限次类型余额不足抛 400；不限次类型只记流水。
     */
    @Transactional
    public Map<String, Object> consume(Long userId, String typeCode, BigDecimal days,
                                       String ref, String reason, Long instanceId) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请假时长必须大于 0");
        }
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        if (!type.isLimited()) {
            // 不限次类型（如病假）：只记流水，不校验余额
            LeaveTransaction txn = new LeaveTransaction();
            txn.setUserId(userId);
            txn.setLeaveTypeId(type.getId());
            txn.setDelta(days.negate());
            txn.setReason(reason);
            txn.setRefInstanceNo(ref);
            txn.setTxnType("CONSUME");
            txn.setInstanceId(instanceId);
            leaveTransactionRepository.save(txn);
            return toBalanceMap(balance, type);
        }
        if (ref != null && !ref.isBlank()) {
            boolean consumed = leaveTransactionRepository
                    .findConsumedByRef(userId, type.getId(), ref)
                    .isPresent();
            if (consumed) {
                return toBalanceMap(balance, type);
            }
        }
        if (days.compareTo(balance.available()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "余额不足：" + type.getName() + " 可用 " + trim0(balance.available())
                    + " " + unitLabel(type) + "，申请 " + trim0(days) + " " + unitLabel(type));
        }
        // 已冻结的部分转已用；未冻结的（旧数据/直接调用）不动 frozen
        balance.setFrozen(nvl(balance.getFrozen()).subtract(days).max(BigDecimal.ZERO));
        balance.setUsed(nvl(balance.getUsed()).add(days));
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, days.negate(), reason, ref, "CONSUME", userId, instanceId);
        return toBalanceMap(balance, type);
    }

    /** 冲销（驳回/撤回/拒绝时调用）。ref 幂等。 */
    @Transactional
    public Map<String, Object> reverse(Long userId, String typeCode, BigDecimal days,
                                       String ref, String reason, Long instanceId) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请假时长必须大于 0");
        }
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        if (!type.isLimited()) {
            return toBalanceMap(balance, type);
        }
        if (ref != null && !ref.isBlank()) {
            boolean reversed = leaveTransactionRepository
                    .findConsumedByRef(userId, type.getId(), ref)
                    .map(t -> hasReversePair(userId, type.getId(), ref, t.getId()))
                    .orElse(false);
            if (!reversed) {
                balance.setUsed(nvl(balance.getUsed()).subtract(days).max(BigDecimal.ZERO));
                leaveBalanceRepository.save(balance);
                recordTransaction(userId, type, days, reason, ref, "REVERSE", userId, instanceId);
            }
            return toBalanceMap(balance, type);
        }
        balance.setUsed(nvl(balance.getUsed()).subtract(days).max(BigDecimal.ZERO));
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, days, reason, ref, "REVERSE", userId, instanceId);
        return toBalanceMap(balance, type);
    }

    /** 冻结（发起请假时）：frozen 增量，流水记 0 + 冻结标记；不限次/未知类型跳过；余额不足 400。幂等（同 ref）。 */
    @Transactional
    public void freeze(Long userId, String codeOrLabel, BigDecimal days, String ref, Long instanceId) {
        LeaveType type = resolveType(codeOrLabel);
        if (type == null || !type.isLimited() || days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (hasMarker(userId, type.getId(), ref, "冻结")) {
            return;
        }
        LeaveBalance balance = ensureBalance(userId, type);
        if (balance.available().compareTo(days) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    type.getName() + "余额不足（可用 " + trim0(balance.available())
                            + " " + unitLabel(type) + "，申请 " + trim0(days) + " " + unitLabel(type) + "）");
        }
        balance.setFrozen(nvl(balance.getFrozen()).add(days));
        leaveBalanceRepository.save(balance);
        LeaveTransaction txn = new LeaveTransaction();
        txn.setUserId(userId);
        txn.setLeaveTypeId(type.getId());
        txn.setDelta(BigDecimal.ZERO);
        txn.setReason("冻结");
        txn.setRefInstanceNo(ref);
        txn.setTxnType("FREEZE");
        txn.setInstanceId(instanceId);
        leaveTransactionRepository.save(txn);
    }

    /** 释放（驳回/拒绝/撤回时）：frozen 回滚，幂等（同 ref）。 */
    @Transactional
    public void release(Long userId, String codeOrLabel, BigDecimal days, String ref, Long instanceId) {
        LeaveType type = resolveType(codeOrLabel);
        if (type == null || days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (hasMarker(userId, type.getId(), ref, "释放")) {
            return;
        }
        LeaveBalance balance = ensureBalance(userId, type);
        if (!type.isLimited()) {
            balance.setFrozen(BigDecimal.ZERO);
            leaveBalanceRepository.save(balance);
            return;
        }
        balance.setFrozen(nvl(balance.getFrozen()).subtract(days).max(BigDecimal.ZERO));
        leaveBalanceRepository.save(balance);
        LeaveTransaction txn = new LeaveTransaction();
        txn.setUserId(userId);
        txn.setLeaveTypeId(type.getId());
        txn.setDelta(BigDecimal.ZERO);
        txn.setReason("释放");
        txn.setRefInstanceNo(ref);
        txn.setTxnType("RELEASE");
        txn.setInstanceId(instanceId);
        leaveTransactionRepository.save(txn);
    }

    /** 表单里的显示名（如"年假"）或 code（如 ANNUAL）都解析为 code；未知返回 null。 */
    public String resolveCode(String codeOrLabel) {
        if (codeOrLabel == null || codeOrLabel.isBlank()) {
            return null;
        }
        String v = codeOrLabel.trim();
        LeaveType t = resolveType(v);
        return t == null ? null : t.getCode();
    }

    private LeaveType resolveType(String codeOrLabel) {
        if (codeOrLabel == null || codeOrLabel.isBlank()) {
            return null;
        }
        String v = codeOrLabel.trim();
        LeaveType exact = leaveTypeRepository.findByCode(v).orElse(null);
        if (exact != null) {
            return exact;
        }
        LeaveType upper = leaveTypeRepository.findByCode(v.toUpperCase()).orElse(null);
        if (upper != null) {
            return upper;
        }
        return leaveTypeRepository.findAll().stream()
                .filter(t -> v.equals(t.getName()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasMarker(Long userId, Long typeId, String ref, String marker) {
        return leaveTransactionRepository.countByMarker(userId, typeId, ref, marker) > 0;
    }

    private boolean hasRef(Long userId, Long typeId, String ref) {
        return leaveTransactionRepository
                .findTop50ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(userId, typeId)
                .stream()
                .anyMatch(t -> ref.equals(t.getRefInstanceNo()));
    }

    private boolean hasReversePair(Long userId, Long typeId, String ref, Long consumeTxnId) {
        return leaveTransactionRepository
                .findTop50ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(userId, typeId)
                .stream()
                .anyMatch(t -> t.getId() > consumeTxnId && t.getDelta() != null
                        && t.getDelta().compareTo(BigDecimal.ZERO) > 0
                        && ref.equals(t.getRefInstanceNo()));
    }

    // ==================== 内部 ====================

    /** 公共：code 或中文名解析（T2 类型管理复用）。 */
    public LeaveType requireType(String codeOrLabel) {
        LeaveType t = resolveType(codeOrLabel);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "假期类型不存在: " + codeOrLabel);
        }
        return t;
    }

    private LeaveBalance ensureBalance(Long userId, LeaveType type) {
        return leaveBalanceRepository.findByUserIdAndLeaveTypeId(userId, type.getId())
                .orElseGet(() -> {
                    LeaveBalance balance = new LeaveBalance();
                    balance.setUserId(userId);
                    balance.setLeaveTypeId(type.getId());
                    return leaveBalanceRepository.save(balance);
                });
    }

    private void recordTransaction(Long userId, LeaveType type, BigDecimal delta, String reason,
                                   String ref, String txnType, Long operatorId, Long instanceId) {
        LeaveTransaction txn = new LeaveTransaction();
        txn.setUserId(userId);
        txn.setLeaveTypeId(type.getId());
        txn.setDelta(delta);
        txn.setReason(reason == null || reason.isBlank() ? "调整" : reason);
        txn.setRefInstanceNo(ref);
        txn.setTxnType(txnType);
        txn.setOperatorId(operatorId);
        txn.setInstanceId(instanceId);
        leaveTransactionRepository.save(txn);
    }

    private boolean matchesKeyword(User u, String kw) {
        String k = kw.trim().toLowerCase();
        return (u.getRealName() != null && u.getRealName().contains(kw.trim()))
                || (u.getUsername() != null && u.getUsername().toLowerCase().contains(k))
                || (u.getEmail() != null && u.getEmail().toLowerCase().contains(k));
    }

    private String unitLabel(LeaveType type) {
        return switch (type.getUnit() == null ? "day" : type.getUnit()) {
            case "hour" -> "小时";
            case "half_day" -> "半天";
            default -> "天";
        };
    }

    /** 流水 → 管理端 Map（HD-03 列 + HD-01 跳转）。批量调用时通过缓存避免 N+1。 */
    private Map<String, Object> toTxnMap(LeaveTransaction t, LeaveType type) {
        Map<Long, User> cache = new java.util.HashMap<>();
        return toTxnMap(t, type, cache);
    }

    private Map<String, Object> toTxnMap(LeaveTransaction t, LeaveType type, Map<Long, User> userCache) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("typeCode", type == null ? null : type.getCode());
        m.put("typeName", type == null ? null : type.getName());
        m.put("unit", type == null ? "day" : type.getUnit());
        m.put("delta", t.getDelta());
        m.put("txnType", t.getTxnType());
        m.put("typeLabel", txnLabel(t.getTxnType()));
        m.put("operatorId", t.getOperatorId());
        m.put("operatorName", userName(t.getOperatorId(), userCache));
        m.put("acceptName", userName(t.getUserId(), userCache));
        m.put("reason", t.getReason());
        m.put("remark", t.getRemark());
        m.put("refInstanceNo", t.getRefInstanceNo());
        m.put("instanceId", t.getInstanceId());
        m.put("createdAt", t.getCreatedAt());
        return m;
    }

    private String userName(Long id, Map<Long, User> cache) {
        if (id == null) {
            return null;
        }
        User u = cache.get(id);
        if (u == null) {
            u = userRepository.findById(id).orElse(null);
            if (u != null) {
                cache.put(id, u);
            }
        }
        return u == null ? null : u.getRealName();
    }

    private static String txnLabel(String txnType) {
        return switch (txnType == null ? "GRANT" : txnType) {
            case "GRANT" -> "授予额度";
            case "ADJUST" -> "调整额度";
            case "QUOTA" -> "修改配额";
            case "CONSUME" -> "审批扣减";
            case "REVERSE" -> "冲销退回";
            case "FREEZE" -> "冻结";
            case "RELEASE" -> "释放";
            default -> txnType;
        };
    }

    private Map<String, Object> toTypeMap(LeaveType type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", type.getId());
        m.put("code", type.getCode());
        m.put("name", type.getName());
        m.put("category", type.getCategory());
        m.put("quotaType", type.getQuotaType());
        m.put("limited", type.isLimited());
        m.put("annualQuota", type.getAnnualQuota());
        m.put("unit", type.getUnit());
        m.put("weight", type.getWeight());
        m.put("enabled", type.getEnabled());
        return m;
    }

    private Map<String, Object> toBalanceMap(LeaveBalance balance, LeaveType type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", type.getCode());
        m.put("name", type.getName());
        m.put("quotaType", type.getQuotaType());
        m.put("limited", type.isLimited());
        m.put("unit", type.getUnit());
        m.put("weight", type.getWeight());
        m.put("quota", nvl(balance.getQuota()));
        m.put("used", nvl(balance.getUsed()));
        m.put("frozen", nvl(balance.getFrozen()));
        m.put("available", balance.available());
        return m;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trim0(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
