package com.oa.service;

import com.oa.dto.LeaveTypeRequest;
import com.oa.entity.LeaveBalance;
import com.oa.entity.LeaveTransaction;
import com.oa.entity.LeaveType;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.enums.UserStatus;
import com.oa.repository.LeaveBalanceRepository;
import com.oa.repository.LeaveTransactionRepository;
import com.oa.repository.LeaveTypeRepository;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import com.oa.enums.UserStatus;
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
    private final OrganizationRepository organizationRepository;

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

    // ==================== HD-01/02/03/08：假期管理（ADMIN） ====================

    /** 部门列表（HD-01 筛选下拉）：全部部门 + 在职人数。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminDepartments() {
        List<User> active = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE).toList();
        return organizationRepository.findAll().stream()
                .filter(o -> "DEPT".equalsIgnoreCase(o.getOrgType()))
                .sorted(java.util.Comparator.comparing(
                        com.oa.entity.Organization::getSortOrder,
                        java.util.Comparator.nullsLast(Integer::compareTo)))
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("orgCode", o.getOrgCode());
                    m.put("orgName", o.getOrgName());
                    long count = active.stream()
                            .filter(u -> o.getId().equals(u.getOrg() == null ? null : u.getOrg().getId()))
                            .count();
                    m.put("memberCount", count);
                    return m;
                }).toList();
    }

    /**
     * 员工列表（HD-01）：按部门/关键字筛选在职员工，嵌套各假期余额；
     * 不限额类型 quota/used/frozen/available 返回 null（前端显示“不限额”）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminEmployees(Long orgId, String typeCode, String keyword) {
        List<LeaveType> types = selectTypes(typeCode);
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> orgId == null || orgId.equals(u.getOrg() == null ? null : u.getOrg().getId()))
                .filter(u -> keyword == null || keyword.isBlank() || matchesKeyword(u, keyword))
                .toList();
        if (users.isEmpty()) {
            return List.of();
        }
        Map<Long, LeaveBalance> balanceByPair = loadBalances(
                users.stream().map(User::getId).toList(),
                types.stream().map(LeaveType::getId).toList());
        return users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("orgId", u.getOrg() == null ? null : u.getOrg().getId());
            m.put("orgName", u.getOrg() == null ? null : u.getOrg().getOrgName());
            m.put("position", u.getPosition());
            m.put("balances", balancesFor(u, types, balanceByPair));
            return m;
        }).toList();
    }

    /** 单员工全部余额（HD-02 余额详情页数据源）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminBalances(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户不存在"));
        List<LeaveType> types = selectTypes(null);
        Map<Long, LeaveBalance> balanceByPair = loadBalances(
                List.of(u.getId()), types.stream().map(LeaveType::getId).toList());
        return balancesFor(u, types, balanceByPair);
    }

    private List<LeaveType> selectTypes(String typeCode) {
        return typeCode == null || typeCode.isBlank()
                ? leaveTypeRepository.findAll().stream()
                        .sorted(java.util.Comparator.comparing(LeaveType::getWeight,
                                java.util.Comparator.reverseOrder()))
                        .toList()
                : List.of(requireType(typeCode));
    }

    private Map<Long, LeaveBalance> loadBalances(List<Long> userIds, List<Long> typeIds) {
        Map<Long, LeaveBalance> map = new java.util.HashMap<>();
        if (userIds.isEmpty() || typeIds.isEmpty()) {
            return map;
        }
        for (LeaveBalance b : leaveBalanceRepository.findByUserIdInAndLeaveTypeIdIn(userIds, typeIds)) {
            map.put(b.getUserId() * 1_000_000L + b.getLeaveTypeId(), b);
        }
        return map;
    }

    /** 员工×类型的余额行（无限额 → 数值列 null）。 */
    private List<Map<String, Object>> balancesFor(User u, List<LeaveType> types, Map<Long, LeaveBalance> balanceByPair) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LeaveType type : types) {
            LeaveBalance b = balanceByPair.get(u.getId() * 1_000_000L + type.getId());
            boolean has = b != null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", type.getCode());
            m.put("name", type.getName());
            m.put("unit", type.getUnit());
            m.put("limited", type.isLimited());
            m.put("weight", type.getWeight());
            if (has && type.isLimited()) {
                m.put("quota", nvl(b.getQuota()));
                m.put("used", nvl(b.getUsed()));
                m.put("frozen", nvl(b.getFrozen()));
                m.put("available", b.available());
            } else {
                m.put("quota", null);
                m.put("used", null);
                m.put("frozen", null);
                m.put("available", null);
            }
            out.add(m);
        }
        return out;
    }

    /** 管理端日志分页（HD-03/08）：rows 与 adminLogs.items 同源。 */
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

    /** 假期管理日志（HD-03）：items 命名对齐前端；typeLabel 中文标签 + instanceId 直存。 */
    @Transactional(readOnly = true)
    public Map<String, Object> adminLogs(Long userId, String typeCode, int page, int size) {
        Map<String, Object> core = adminLedger(userId, typeCode, null, null, page, size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", core.get("total"));
        out.put("items", core.get("rows"));
        return out;
    }

    /** 余额导出 CSV（HD-08）：员工/部门/假期类型/额度/已用/冻结/剩余/单位。 */
    @Transactional(readOnly = true)
    public String adminExport(Long orgId, String typeCode, String keyword) {
        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM：Excel 中文兼容
        String[] header = {"员工", "账号", "部门", "假期类型", "单位", "额度", "已用", "冻结", "剩余"};
        sb.append(String.join(",", header)).append('\n');
        for (Map<String, Object> emp : adminEmployees(orgId, typeCode, keyword)) {
            for (Map<String, Object> b : (List<Map<String, Object>>) emp.get("balances")) {
                sb.append(csvCell((String) emp.get("realName")))
                        .append(',').append(csvCell((String) emp.get("username")))
                        .append(',').append(csvCell((String) emp.get("orgName")))
                        .append(',').append(csvCell((String) b.get("name")))
                        .append(',').append(csvCell((String) b.get("unit")))
                        .append(',').append(csvCell(balanceText(b.get("quota"), !Boolean.TRUE.equals(b.get("limited")))))
                        .append(',').append(csvCell(balanceText(b.get("used"), !Boolean.TRUE.equals(b.get("limited")))))
                        .append(',').append(csvCell(balanceText(b.get("frozen"), !Boolean.TRUE.equals(b.get("limited")))))
                        .append(',').append(csvCell(balanceText(b.get("available"), !Boolean.TRUE.equals(b.get("limited")))))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private static String balanceText(Object v, boolean unlimited) {
        if (unlimited) {
            return "不限额";
        }
        return v == null ? "" : String.valueOf(v);
    }

    private static String csvCell(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
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
        recordTransaction(userId, type, amount, reason, ref, "GRANT", operatorId, null, null);
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
            txn.setOperatorId(userId);
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
        recordTransaction(userId, type, days.negate(), reason, ref, "CONSUME", userId, instanceId, null);
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
                recordTransaction(userId, type, days, reason, ref, "REVERSE", userId, instanceId, null);
            }
            return toBalanceMap(balance, type);
        }
        balance.setUsed(nvl(balance.getUsed()).subtract(days).max(BigDecimal.ZERO));
        leaveBalanceRepository.save(balance);
        recordTransaction(userId, type, days, reason, ref, "REVERSE", userId, instanceId, null);
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
        txn.setOperatorId(userId);
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
        txn.setOperatorId(userId);
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
                                   String ref, String txnType, Long operatorId, Long instanceId,
                                   String remark) {
        LeaveTransaction txn = new LeaveTransaction();
        txn.setUserId(userId);
        txn.setLeaveTypeId(type.getId());
        txn.setDelta(delta);
        txn.setReason(reason == null || reason.isBlank() ? "调整" : reason);
        txn.setRefInstanceNo(ref);
        txn.setTxnType(txnType);
        txn.setOperatorId(operatorId);
        txn.setInstanceId(instanceId);
        txn.setRemark(remark);
        leaveTransactionRepository.save(txn);
    }

    // ==================== HD-07：授予 / 调整（管理操作，ADMIN） ====================

    /**
     * 额度设定（SET_QUOTA）：quota = newQuota，流水 delta = 新−旧，txnType=QUOTA。
     * 不变式：newQuota ≥ used+frozen，否则拒绝（避免负余额）。
     */
    @Transactional
    public Map<String, Object> setQuota(Long userId, String typeCode, BigDecimal newQuota,
                                        String remark, Long operatorId) {
        if (newQuota == null || newQuota.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "额度必须 >= 0");
        }
        User target = requireActiveUser(userId);
        LeaveType type = requireType(typeCode);
        LeaveBalance b = ensureBalance(target.getId(), type);
        BigDecimal old = nvl(b.getQuota());
        BigDecimal inUse = nvl(b.getUsed()).add(nvl(b.getFrozen()));
        if (newQuota.compareTo(inUse) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "额度不能低于已用+冻结（" + inUse + "）");
        }
        if (newQuota.compareTo(old) == 0) {
            return toBalanceMap(b, type);
        }
        b.setQuota(newQuota);
        leaveBalanceRepository.save(b);
        recordTransaction(target.getId(), type, newQuota.subtract(old), "额度设定",
                null, "QUOTA", operatorId, null, remark);
        return toBalanceMap(b, type);
    }

    /**
     * 剩余时长修正（SET_REMAINING）：不限额类型拒绝（无剩余概念）；
     * 限额类型 quota = used+frozen+newRemaining（修正总额度使剩余达标），流水 txnType=ADJUST。
     */
    @Transactional
    public Map<String, Object> setRemaining(Long userId, String typeCode, BigDecimal newRemaining,
                                            String remark, Long operatorId) {
        if (newRemaining == null || newRemaining.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "剩余时长必须 >= 0");
        }
        User target = requireActiveUser(userId);
        LeaveType type = requireType(typeCode);
        if (!type.isLimited()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不限额类型无剩余时长概念");
        }
        LeaveBalance b = ensureBalance(target.getId(), type);
        BigDecimal old = nvl(b.getQuota());
        BigDecimal newQuota = nvl(b.getUsed()).add(nvl(b.getFrozen())).add(newRemaining);
        if (newQuota.compareTo(old) == 0) {
            return toBalanceMap(b, type);
        }
        b.setQuota(newQuota);
        leaveBalanceRepository.save(b);
        recordTransaction(target.getId(), type, newQuota.subtract(old), "剩余时长调整",
                null, "ADJUST", operatorId, null, remark);
        return toBalanceMap(b, type);
    }

    /** 管理操作目标必须为在职员工（需求 5.4.2）。 */
    private User requireActiveUser(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标用户不存在"));
        if (u.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "目标用户非在职（当前状态: " + u.getStatus() + "）");
        }
        return u;
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
