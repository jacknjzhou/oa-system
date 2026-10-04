package com.oa;

import com.oa.dto.ApprovalPermissionRequest;
import com.oa.service.ProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 审批功能权限（P2-3）：模板 × 角色 × 权限项。 */
@SpringBootTest
class ApprovalPermissionTests {

    @Autowired
    private ProcessService processService;

    private Long reimbursementDefId() {
        return processService.listDefinitions(true).stream()
                .filter(d -> "reimbursement".equals(d.getDefKey()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void saveAndListPermissions_roundTrip() {
        Long defId = reimbursementDefId();
        ApprovalPermissionRequest req = new ApprovalPermissionRequest();
        req.setRoleCode("MANAGER");
        req.setPerms(List.of("start", "view"));
        Map<String, Object> saved = processService.savePermissions(defId, List.of(req));
        assertThat(saved.get("count")).isEqualTo(2);

        List<Map<String, Object>> rows = processService.listPermissions(defId);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.get("roleCode")).isEqualTo("MANAGER"));
        assertThat(rows.stream().map(r -> (String) r.get("perm")).toList())
                .containsExactlyInAnyOrder("start", "view");
    }

    @Test
    void savePermissions_rejectsUnknownPermOrRole() {
        Long defId = reimbursementDefId();
        ApprovalPermissionRequest bad = new ApprovalPermissionRequest();
        bad.setRoleCode("MANAGER");
        bad.setPerms(List.of("superpower"));
        assertThatThrownBy(() -> processService.savePermissions(defId, List.of(bad)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("未知权限项");

        ApprovalPermissionRequest badRole = new ApprovalPermissionRequest();
        badRole.setRoleCode("NO_SUCH_ROLE");
        badRole.setPerms(List.of("view"));
        assertThatThrownBy(() -> processService.savePermissions(defId, List.of(badRole)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("角色不存在");
    }
}
