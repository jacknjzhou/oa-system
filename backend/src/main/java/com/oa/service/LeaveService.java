package com.oa.service;

import com.oa.entity.LeaveBalance;
import com.oa.entity.LeaveTransaction;
import com.oa.entity.LeaveType;
import com.oa.repository.LeaveBalanceRepository;
import com.oa.repository.LeaveTransactionRepository;
import com.oa.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假期服务（AT-03 三账本）：
 * 余额账（leave_balance.quota/used/frozen）+ 发生账（leave_transaction）+ 额度账（leave_type.annual_quota）。
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTransactionRepository leaveTransactionRepository;

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTypes() {
        return leaveTypeRepository.findByEnabledTrueOrderByWeightAscIdAsc().stream()
                .map(this::toTypeMap)
                .toList();
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
        List<Map<String, Object>> txnMaps = new ArrayList<>();
        for (LeaveTransaction t : txns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("delta", t.getDelta());
            m.put("reason", t.getReason());
            m.put("refInstanceNo", t.getRefInstanceNo());
            m.put("createdAt", t.getCreatedAt());
            txnMaps.add(m);
        }
        row.put("transactions", txnMaps);
        return List.of(row);
    }

    // ==================== 变动 ====================

    /** 授予/调整额度（quota 增量 + 流水）。ref 非空时幂等：同 ref 已授则跳过。 */
    @Transactional
    public Map<String, Object> grant(Long userId, String typeCode, int amount, String reason) {
        return grant(userId, typeCode, amount, reason, null);
    }

    @Transactional
    public Map<String, Object> grant(Long userId, String typeCode, int amount, String reason, String ref) {
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        if (ref != null && !ref.isBlank() && hasRef(userId, type.getId(), ref)) {
            return toBalanceMap(balance, type);
        }
        balance.setQuota(nvl(balance.getQuota()) + amount);
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, amount, reason, ref);
        return toBalanceMap(balance, type);
    }

    private boolean hasRef(Long userId, Long typeId, String ref) {
        return leaveTransactionRepository
                .findTop50ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(userId, typeId)
                .stream()
                .anyMatch(t -> ref.equals(t.getRefInstanceNo()));
    }

    /**
     * 扣减（请假审批通过后调用）。ref 幂等：同一实例重复完成不重复扣。
     * 余额不足抛 400。
     */
    @Transactional
    public Map<String, Object> consume(Long userId, String typeCode, int days, String ref, String reason) {
        if (days <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请假天数必须大于 0");
        }
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        if ("none".equals(type.getQuotaType())) {
            // 不限额类型（如病假）：只记流水，不校验余额
            LeaveBalance saved = balance;
            LeaveTransaction txn = new LeaveTransaction();
            txn.setUserId(userId);
            txn.setLeaveTypeId(type.getId());
            txn.setDelta(-days);
            txn.setReason(reason);
            txn.setRefInstanceNo(ref);
            leaveTransactionRepository.save(txn);
            return toBalanceMap(saved, type);
        }
        if (ref != null && !ref.isBlank()) {
            boolean consumed = leaveTransactionRepository
                    .findConsumedByRef(userId, type.getId(), ref)
                    .isPresent();
            if (consumed) {
                return toBalanceMap(balance, type);
            }
        }
        if (days > balance.available()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "余额不足：" + type.getName() + " 可用 " + balance.available() + " 天，申请 " + days + " 天");
        }
        // 已冻结的部分转已用；未冻结的（旧数据/直接调用）不动 frozen
        balance.setFrozen(Math.max(0, nvl(balance.getFrozen()) - days));
        balance.setUsed(nvl(balance.getUsed()) + days);
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, -days, reason, ref);
        return toBalanceMap(balance, type);
    }

    /** 冲销（驳回/撤回/拒绝时调用）。ref 幂等。 */
    @Transactional
    public Map<String, Object> reverse(Long userId, String typeCode, int days, String ref, String reason) {
        if (days <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请假天数必须大于 0");
        }
        LeaveType type = requireType(typeCode);
        LeaveBalance balance = ensureBalance(userId, type);
        if ("none".equals(type.getQuotaType())) {
            return toBalanceMap(balance, type);
        }
        if (ref != null && !ref.isBlank()) {
            boolean reversed = leaveTransactionRepository
                    .findConsumedByRef(userId, type.getId(), ref)
                    .map(t -> hasReversePair(userId, type.getId(), ref, t.getId()))
                    .orElse(false);
            if (!reversed) {
                int usedBefore = nvl(balance.getUsed());
                balance.setUsed(Math.max(0, usedBefore - days));
                leaveBalanceRepository.save(balance);
                recordTransaction(userId, type, days, reason, ref);
            }
            return toBalanceMap(balance, type);
        }
        balance.setUsed(Math.max(0, nvl(balance.getUsed()) - days));
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, days, reason, ref);
        return toBalanceMap(balance, type);
    }

    private boolean hasReversePair(Long userId, Long typeId, String ref, Long consumeTxnId) {
        return leaveTransactionRepository
                .findTop50ByUserIdAndLeaveTypeIdOrderByCreatedAtDescIdDesc(userId, typeId)
                .stream()
                .anyMatch(t -> t.getId() > consumeTxnId && t.getDelta() > 0
                        && ref.equals(t.getRefInstanceNo()));
    }

    /** 表单里的显示名（如“年假”）或 code（如 ANNUAL）都解析为 code；未知返回 null。 */
    public String resolveCode(String codeOrLabel) {
        if (codeOrLabel == null || codeOrLabel.isBlank()) {
            return null;
        }
        String v = codeOrLabel.trim();
        LeaveType t = resolveType(v);
        return t == null ? null : t.getCode();
    }

    /** 冻结（发起请假时）：frozen 增量，流水记 0 天 + 标记；不限额/未知类型跳过；余额不足 400。幂等（同 ref）。 */
    @Transactional
    public void freeze(Long userId, String codeOrLabel, int days, String ref) {
        LeaveType type = resolveType(codeOrLabel);
        if (type == null || "none".equals(type.getQuotaType()) || days <= 0) {
            return;
        }
        if (hasMarker(userId, type.getId(), ref, "冻结")) {
            return;
        }
        LeaveBalance balance = ensureBalance(userId, type);
        if (balance.available() < days) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    type.getName() + "余额不足（可用 " + balance.available() + " 天，申请 " + days + " 天）");
        }
        balance.setFrozen(nvl(balance.getFrozen()) + days);
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, 0, "冻结", ref);
    }

    /** 释放（驳回/拒绝/撤回时）：frozen 回滚，幂等（同 ref）。 */
    @Transactional
    public void release(Long userId, String codeOrLabel, int days, String ref) {
        LeaveType type = resolveType(codeOrLabel);
        if (type == null || days <= 0) {
            return;
        }
        if (hasMarker(userId, type.getId(), ref, "释放")) {
            return;
        }
        LeaveBalance balance = ensureBalance(userId, type);
        if ("none".equals(type.getQuotaType())) {
            balance.setFrozen(0);
            leaveBalanceRepository.save(balance);
            return;
        }
        balance.setFrozen(Math.max(0, nvl(balance.getFrozen()) - days));
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, 0, "释放", ref);
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

    // ==================== 内部 ====================

    private LeaveType requireType(String typeCode) {
        return leaveTypeRepository.findByCode(typeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "假期类型不存在: " + typeCode));
    }

    private LeaveBalance ensureBalance(Long userId, LeaveType type) {
        return leaveBalanceRepository.findByUserIdAndLeaveTypeId(userId, type.getId())
                .orElseGet(() -> {
                    LeaveBalance balance = new LeaveBalance();
                    balance.setUserId(userId);
                    balance.setLeaveTypeId(type.getId());
                    balance.setQuota(0);
                    balance.setUsed(0);
                    balance.setFrozen(0);
                    return leaveBalanceRepository.save(balance);
                });
    }

    private void recordTransaction(Long userId, LeaveType type, int delta, String reason, String ref) {
        LeaveTransaction txn = new LeaveTransaction();
        txn.setUserId(userId);
        txn.setLeaveTypeId(type.getId());
        txn.setDelta(delta);
        txn.setReason(reason == null || reason.isBlank() ? "调整" : reason);
        txn.setRefInstanceNo(ref);
        leaveTransactionRepository.save(txn);
    }

    private Map<String, Object> toTypeMap(LeaveType type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", type.getId());
        m.put("code", type.getCode());
        m.put("name", type.getName());
        m.put("category", type.getCategory());
        m.put("quotaType", type.getQuotaType());
        m.put("annualQuota", type.getAnnualQuota());
        return m;
    }

    private Map<String, Object> toBalanceMap(LeaveBalance balance, LeaveType type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", type.getCode());
        m.put("name", type.getName());
        m.put("quotaType", type.getQuotaType());
        m.put("quota", nvl(balance.getQuota()));
        m.put("used", nvl(balance.getUsed()));
        m.put("frozen", nvl(balance.getFrozen()));
        m.put("available", balance.available());
        return m;
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
