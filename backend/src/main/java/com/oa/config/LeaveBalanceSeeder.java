package com.oa.config;

import com.oa.entity.LeaveType;
import com.oa.entity.User;
import com.oa.repository.LeaveTypeRepository;
import com.oa.repository.UserRepository;
import com.oa.service.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 假期种子（AT-03/HD-05）：预置 10 类假期 + 给演示用户授予年假额度。
 * 属性表与 V22 迁移一致（9 类规格类型 + SPECIAL 演示类型）。
 * 幂等：类型按 code 判存在；演示授权按 SEED- 前缀流水判存在。
 * 注意：不移动演示用户到部门（四裁决④：部门与演示账号解耦）。
 */
@Slf4j
@Order(300)
@Component
@RequiredArgsConstructor
public class LeaveBalanceSeeder implements ApplicationRunner {

    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final LeaveService leaveService;

    /** code, name, category, quotaType, annualQuota(不限次=0), unit, weight（规格 5.2） */
    private static final String[][] TYPES = {
            {"ANNUAL", "年假", "法定", "fixed", "99", "day", "99"},
            {"PERSONAL", "事假", "法定", "none", "0", "hour", "98"},
            {"SICK", "病假", "法定", "none", "0", "half_day", "97"},
            {"COMPENSATORY", "调休假", "法定", "none", "0", "hour", "96"},
            {"MARRIAGE", "婚假", "法定", "none", "0", "day", "95"},
            {"MATERNITY", "产假", "法定", "none", "0", "day", "94"},
            {"PATERNAL", "陪产假", "法定", "none", "0", "day", "93"},
            {"BEREAVEMENT", "丧假", "法定", "none", "0", "day", "92"},
            {"MENSTRUAL", "例假", "法定", "none", "0", "day", "0"},
            {"SPECIAL", "特殊假", "公司", "none", "0", "day", "90"},
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (String[] t : TYPES) {
            if (leaveTypeRepository.findByCode(t[0]).isPresent()) {
                continue;
            }
            LeaveType type = new LeaveType();
            type.setCode(t[0]);
            type.setName(t[1]);
            type.setCategory(t[2]);
            type.setQuotaType(t[3]);
            type.setAnnualQuota(Integer.parseInt(t[4]));
            type.setUnit(t[5]);
            type.setWeight(Integer.parseInt(t[6]));
            leaveTypeRepository.save(type);
            created++;
        }
        if (created > 0) {
            log.info("已预置 {} 类假期类型", created);
        }

        // 演示用户授额：仅年假 10（事假按规格为不限次，不再授予；幂等：SEED-ANNUAL 已授则跳过）
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                leaveService.grant(user.getId(), "ANNUAL", BigDecimal.TEN,
                        "年度额度授予（演示数据）", "SEED-ANNUAL", null);
            } catch (Exception e) {
                log.warn("授予 {} ANNUAL 失败: {}", user.getUsername(), e.getMessage());
            }
        }
        log.info("演示假期额度初始化完成（{} 用户 × 年假 10）", users.size());
    }
}
