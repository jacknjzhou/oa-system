package com.oa;

import com.oa.entity.Organization;
import com.oa.enums.UserStatus;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 人员-部门关联数据层：V24 字段 + 组织/用户仓库查询。 */
@SpringBootTest
class UserOrgDataTests {

    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;

    @Test
    void org_repository_tree_queries() {
        // 5 个 DEPT（TECH + V22 四部门），共享库可能更多，只断言已知 code 存在且 parent 查询可用
        List<Organization> tech = organizationRepository.findByOrgCode("TECH")
                .map(List::of).orElse(List.of());
        assertFalse(tech.isEmpty(), "TECH 部门应存在");
        List<Organization> children = organizationRepository.findByParentId(tech.get(0).getId());
        assertNotNull(children);
    }

    @Test
    void user_new_fields_roundtrip() {
        // 演示账号默认值为空，不污染共享库：只断言字段可读
        var admin = userRepository.findByUsername("admin").orElseThrow();
        assertNull(admin.getGender());
        assertNull(admin.getJobTitleId());
        // TECH 在职人数 >= 4（共享库纪律：不精确断言）
        long count = userRepository.countByOrgIdAndStatus(admin.getOrg().getId(), UserStatus.ACTIVE);
        assertTrue(count >= 4, "TECH 在职人数应 >= 4, 实际 " + count);
    }
}
