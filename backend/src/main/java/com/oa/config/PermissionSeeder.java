package com.oa.config;

import com.oa.entity.Permission;
import com.oa.entity.PermissionGroup;
import com.oa.entity.User;
import com.oa.repository.PermissionGroupRepository;
import com.oa.repository.PermissionRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 权限清单与权组预置（SY-03，幂等）：
 * 十大分组约 28 项内置权限；系统管理员组=全部权限（admin），普通组=基础权限（其余用户）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(350)
public class PermissionSeeder implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final PermissionGroupRepository groupRepository;
    private final UserRepository userRepository;

    private static final List<String[]> PERMS = List.of(
            // 审批
            new String[]{"approval:start", "发起审批", "审批"},
            new String[]{"approval:view", "查看审批", "审批"},
            new String[]{"approval:manage", "审批管理", "审批"},
            new String[]{"approval:template", "审批模板管理", "审批"},
            // 考勤
            new String[]{"attendance:clock", "考勤打卡", "考勤"},
            new String[]{"attendance:view", "考勤查看", "考勤"},
            new String[]{"attendance:manage", "考勤管理", "考勤"},
            // 假期
            new String[]{"leave:apply", "请假申请", "假期"},
            new String[]{"leave:view", "假期查看", "假期"},
            new String[]{"leave:manage", "假期管理", "假期"},
            // 公文
            new String[]{"document:create", "公文拟制", "公文"},
            new String[]{"document:view", "公文查看", "公文"},
            new String[]{"document:manage", "公文管理", "公文"},
            // 印章
            new String[]{"seal:apply", "用印申请", "印章"},
            new String[]{"seal:manage", "用印管理", "印章"},
            // 合同
            new String[]{"contract:apply", "合同申请", "合同"},
            new String[]{"contract:manage", "合同管理", "合同"},
            // 费用
            new String[]{"expense:apply", "费用申请", "费用"},
            new String[]{"expense:view", "费用查看", "费用"},
            new String[]{"expense:reimburse", "报销管理", "费用"},
            // 采购
            new String[]{"procurement:apply", "采购申请", "采购"},
            new String[]{"procurement:manage", "采购管理", "采购"},
            // 人事
            new String[]{"hr:user", "员工管理", "人事"},
            new String[]{"hr:dept", "部门管理", "人事"},
            new String[]{"hr:level", "职级职称管理", "人事"},
            // 系统
            new String[]{"system:settings", "系统设置", "系统"},
            new String[]{"system:permission", "权组与权限", "系统"},
            new String[]{"system:log", "登录日志", "系统"},
            new String[]{"system:company", "企业信息", "系统"}
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String[] p : PERMS) {
            if (permissionRepository.findByCode(p[0]).isEmpty()) {
                Permission perm = new Permission();
                perm.setCode(p[0]);
                perm.setName(p[1]);
                perm.setGroupName(p[2]);
                permissionRepository.save(perm);
            }
        }

        // 系统管理员组 = 全部权限
        PermissionGroup adminGroup = groupRepository.findByCode("SYSTEM_ADMIN").orElseGet(() -> {
            PermissionGroup g = new PermissionGroup();
            g.setCode("SYSTEM_ADMIN");
            g.setName("系统管理员");
            return groupRepository.save(g);
        });
        adminGroup.getPermissions().clear();
        adminGroup.getPermissions().addAll(permissionRepository.findAllByOrderByIdAsc());
        groupRepository.save(adminGroup);

        // 普通组 = 基础权限
        String[] basic = {
                "approval:start", "approval:view", "attendance:clock", "attendance:view",
                "leave:apply", "leave:view", "document:create", "document:view",
                "seal:apply", "contract:apply", "expense:apply", "expense:view",
                "procurement:apply"
        };
        PermissionGroup normalGroup = groupRepository.findByCode("NORMAL").orElseGet(() -> {
            PermissionGroup g = new PermissionGroup();
            g.setCode("NORMAL");
            g.setName("普通组");
            return groupRepository.save(g);
        });
        normalGroup.getPermissions().clear();
        for (String code : basic) {
            permissionRepository.findByCode(code).ifPresent(normalGroup.getPermissions()::add);
        }
        groupRepository.save(normalGroup);

        // 成员：admin → 系统管理员；其余 → 普通组（幂等，只增不减）
        for (User user : userRepository.findAll()) {
            boolean admin = user.getRoleCodes().stream().anyMatch("ADMIN"::equals);
            if (admin) {
                adminGroup.getMembers().add(user);
                normalGroup.getMembers().remove(user);
            } else {
                normalGroup.getMembers().add(user);
            }
        }
        groupRepository.save(adminGroup);
        groupRepository.save(normalGroup);
        log.info("权组/权限预置完成：权限 {} 项，权组 {} 个",
                permissionRepository.count(), groupRepository.count());
    }
}
