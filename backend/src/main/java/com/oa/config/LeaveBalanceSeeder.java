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

import java.util.List;

/**
 * 假期种子（AT-03）：预置 6 类假期 + 给演示用户授予额度（幂等：按 code / 首条流水判断）。
 */
@Slf4j
@Order(300)
@Component
@RequiredArgsConstructor
public class LeaveBalanceSeeder implements ApplicationRunner {

    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final LeaveService leaveService;

    private static final String[][] TYPES = {
            {"ANNUAL", "年假", "法定", "fixed", "10"},
            {"SICK", "病假", "法定", "none", "0"},
            {"PERSONAL", "事假", "法定", "fixed", "5"},
            {"MARRIAGE", "婚假", "法定", "fixed", "10"},
            {"PATERNAL", "陪产假", "法定", "fixed", "15"},
            {"SPECIAL", "特殊假", "公司", "none", "0"},
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
            type.setWeight(100 + created);
            leaveTypeRepository.save(type);
            created++;
        }
        if (created > 0) {
            log.info("已预置 {} 类假期类型", created);
        }

        // 演示用户授额：年假 10 + 事假 5（幂等：已有该类型余额则跳过）
        List<User> users = userRepository.findAll();
        for (User user : users) {
            for (String code : List.of("ANNUAL", "PERSONAL")) {
                int days = "ANNUAL".equals(code) ? 10 : 5;
                try {
                    leaveService.grant(user.getId(), code, days, "年度额度授予（演示数据）", "SEED-" + code);
                } catch (Exception e) {
                    log.warn("授予 {} {} 失败: {}", user.getUsername(), code, e.getMessage());
                }
            }
        }
        log.info("演示假期额度初始化完成（{} 用户 × 2 类型）", users.size());
    }
}
