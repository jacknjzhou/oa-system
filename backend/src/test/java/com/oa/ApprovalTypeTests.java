package com.oa;

import com.oa.dto.ApprovalTypeDTO;
import com.oa.dto.ApprovalTypeRequest;
import com.oa.dto.InstanceDTO;
import com.oa.dto.ProcessStartRequest;
import com.oa.repository.ApprovalTypeRepository;
import com.oa.service.ApprovalTypeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 审批类型（P2-1a）：CRUD、启停、删除保护（被实例引用时禁止删除）、预置报销类型。
 */
@SpringBootTest
class ApprovalTypeTests {

    @Autowired
    private ApprovalTypeService approvalTypeService;

    @Autowired
    private ApprovalTypeRepository approvalTypeRepository;

    @Autowired
    private com.oa.service.ProcessService processService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    private Long reimbursementDefId() {
        return processService.listDefinitions(true).stream()
                .filter(d -> "reimbursement".equals(d.getDefKey()))
                .findFirst().orElseThrow().getId();
    }

    private ApprovalTypeRequest req(String code, String name, Long defId) {
        ApprovalTypeRequest r = new ApprovalTypeRequest();
        r.setCode(code);
        r.setName(name);
        r.setCategory("财务");
        r.setIcon("🧾");
        r.setWeight(10);
        r.setDefId(defId);
        r.setEnabled(true);
        return r;
    }

    @Test
    void seeded_reimbursement_type_points_to_reimbursement_def() {
        List<ApprovalTypeDTO> all = approvalTypeService.listAll();
        assertThat(all.stream().map(ApprovalTypeDTO::getCode)).contains("REIMBURSEMENT");
        ApprovalTypeDTO reimbursement = all.stream()
                .filter(t -> "REIMBURSEMENT".equals(t.getCode())).findFirst().orElseThrow();
        assertThat(reimbursement.getName()).isEqualTo("报销");
        assertThat(reimbursement.getDefName()).contains("报销");
    }

    @Test
    void create_and_list_includes_defName_and_enabledFilter() {
        Long defId = reimbursementDefId();
        approvalTypeService.create(req("TRAVEL", "差旅", defId));
        approvalTypeService.create(req("CONTRACT", "合同", defId));

        ApprovalTypeDTO contract = approvalTypeService.listAll().stream()
                .filter(t -> "CONTRACT".equals(t.getCode())).findFirst().orElseThrow();
        ApprovalTypeRequest off = req("CONTRACT", "合同", contract.getDefId());
        off.setEnabled(false);
        approvalTypeService.update(contract.getId(), off);

        ApprovalTypeDTO travel = approvalTypeService.listAll().stream()
                .filter(t -> "TRAVEL".equals(t.getCode())).findFirst().orElseThrow();
        assertThat(travel.getDefName()).isNotBlank();
        assertThat(travel.getEnabled()).isTrue();

        List<ApprovalTypeDTO> enabled = approvalTypeService.listEnabled();
        assertThat(enabled.stream().map(ApprovalTypeDTO::getCode))
                .contains("REIMBURSEMENT", "TRAVEL")
                .doesNotContain("CONTRACT");
    }

    @Test
    void create_duplicate_code_rejected() {
        Long defId = reimbursementDefId();
        approvalTypeService.create(req("EXPENSE", "费用结算", defId));
        assertThatThrownBy(() -> approvalTypeService.create(req("EXPENSE", "费用结算重复", defId)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_missing_definition_rejected() {
        assertThatThrownBy(() -> approvalTypeService.create(req("GHOST", "幽灵类型", 999999L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("流程模板");
    }

    @Test
    void delete_protected_when_instances_exist() {
        loginAs("employee");
        Long defId = reimbursementDefId();
        ApprovalTypeDTO type = approvalTypeService.create(req("SETTLE", "结算单", defId));

        ProcessStartRequest start = new ProcessStartRequest();
        start.setDefId(defId);
        start.setTitle("引用保护单");
        start.setBusinessType("SETTLE");
        InstanceDTO instance = processService.startInstance(start);
        assertThat(instance.getBusinessType()).isEqualTo("SETTLE");

        assertThatThrownBy(() -> approvalTypeService.delete(type.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("停用");

        // 停用后可以删除
        ApprovalTypeRequest off = req("SETTLE", "结算单", type.getDefId());
        off.setEnabled(false);
        approvalTypeService.update(type.getId(), off);
        approvalTypeService.delete(type.getId());
        assertThat(approvalTypeRepository.existsById(type.getId())).isFalse();
    }
}
