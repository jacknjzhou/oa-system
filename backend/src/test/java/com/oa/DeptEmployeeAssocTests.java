package com.oa;

import com.oa.controller.ApprovalTypeController;
import com.oa.controller.ProcessController;
import com.oa.dto.ApiResponse;
import com.oa.dto.ApprovalTypeDTO;
import com.oa.dto.InstanceDTO;
import com.oa.dto.ProcessStartRequest;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import com.oa.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 人员-部门关联收尾（6.4.3 / 验收 1）：
 * 审批实例详情携带发起人部门/岗位（与员工档案一致，空值 null 安全）；调岗后新实例反映新部门。
 */
@SpringBootTest
class DeptEmployeeAssocTests {

    @Autowired
    ProcessController processController;

    @Autowired
    ApprovalTypeController approvalTypeController;


    @Autowired
    UserRepository userRepository;

    @Autowired
    OrganizationRepository organizationRepository;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated(username, null, java.util.List.of()));
    }

    @Test
    void instance_detail_carries_initiator_org_and_position() {
        loginAs("employee");
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        var emp = userRepository.findByUsername("employee").orElseThrow();
        Long empId = emp.getId();
        Organization savedOrg = emp.getOrg();
        String savedPosition = emp.getPosition();

        try {
            // 无部门无岗位 → 详情字段 null（不 NPE）
            emp.setOrg(null);
            emp.setPosition(null);
            emp = userRepository.save(emp);
            String id = startLeave();
            assertNull(instanceOf(id).getInitiatorOrgName(), "无部门时应为 null");
            assertNull(instanceOf(id).getInitiatorPosition(), "无岗位时应为 null");

            // 有部门有岗位 → 详情一致（验收 1）
            emp.setOrg(tech);
            emp.setPosition("T6 测试岗");
            emp = userRepository.save(emp);
            id = startLeave();
            InstanceDTO inst = instanceOf(id);
            assertEquals(tech.getOrgName(), inst.getInitiatorOrgName());
            assertEquals("T6 测试岗", inst.getInitiatorPosition());
        } finally {
            emp.setOrg(savedOrg);
            emp.setPosition(savedPosition);
            userRepository.save(emp);
        }
    }

    @Test
    void instance_reflects_transfer() {
        loginAs("employee");
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        var market = organizationRepository.findByOrgCode("MARKET").orElseThrow();
        var emp = userRepository.findByUsername("employee").orElseThrow();
        Organization savedOrg = emp.getOrg();

        try {
            emp.setOrg(tech);
            emp = userRepository.save(emp);
            assertEquals(tech.getOrgName(), instanceOf(startLeave()).getInitiatorOrgName());

            // 调岗到 MARKET → 新实例反映新部门（验收 1 列表/详情一致）
            emp.setOrg(market);
            emp = userRepository.save(emp);
            assertEquals(market.getOrgName(), instanceOf(startLeave()).getInitiatorOrgName());
        } finally {
            emp.setOrg(savedOrg);
            userRepository.save(emp);
        }
    }

    /** 发起一个请假流程，返回实例 id（流程会停在主管节点，共享库留有测试实例——可接受）。 */
    private String startLeave() {
        List<ApprovalTypeDTO> types = approvalTypeController.list().getData();
        ApprovalTypeDTO leave = types.stream()
                .filter(t -> "LEAVE".equals(t.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少请假审批类型"));
        ProcessStartRequest req = new ProcessStartRequest();
        req.setDefId(leave.getDefId());
        req.setTitle("T6 测试请假");
        req.setBusinessType("leave");
        req.setBusinessData("{\"leaveTypeCode\":\"PERSONAL\",\"days\":1}");
        var r = processController.startInstance(req);
        assertEquals(200, r.getCode(), "启动流程应成功: " + r.getMessage());
        InstanceDTO dto = r.getData();
        assertNotNull(dto.getId());
        return String.valueOf(dto.getId());
    }

    /** 实例详情（GET /process-instances/{id} → data.instance = InstanceDTO）。 */
    private InstanceDTO instanceOf(String instanceId) {
        var detail = processController.getInstance(Long.valueOf(instanceId));
        assertEquals(200, detail.getCode(), "详情应 200: " + detail.getMessage());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) detail.getData();
        assertNotNull(data.get("instance"), "详情应含 instance 节点: " + data.keySet());
        return (InstanceDTO) data.get("instance");
    }
}
