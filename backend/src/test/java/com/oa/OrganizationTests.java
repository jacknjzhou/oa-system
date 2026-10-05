package com.oa;

import com.oa.controller.OrganizationController;
import com.oa.controller.UserController;
import com.oa.dto.ApiResponse;
import com.oa.dto.OrganizationRequest;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.enums.UserStatus;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import com.oa.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 部门管理（SY-02）：树/CRUD/删除保护/权限。临时部门 T2_ 前缀，@AfterAll 清理。 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationTests {

    @Autowired private OrganizationController organizationController;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserController userController;
    @Autowired private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    private Organization createTmp(String orgName) {
        Organization o = new Organization();
        o.setOrgName(orgName);
        // code 每 JVM 唯一（VARCHAR(8)）：避免跨测试/重跑的唯一键碰撞；cleanup 按 T2_ 前缀统一清理
        o.setOrgCode("T2_" + (System.nanoTime() % 9999));
        o.setOrgType("DEPT");
        o.setStatus(com.oa.enums.EnableStatus.ENABLED);
        return organizationRepository.save(o);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findNode(List<Map<String, Object>> nodes, long id) {
        for (Map<String, Object> n : nodes) {
            if (((Number) n.get("id")).longValue() == id) return n;
            var children = (List<Map<String, Object>>) n.get("children");
            if (children != null) {
                Map<String, Object> hit = findNode(children, id);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /** 清理所有 T2_ 前缀部门：多轮先删「父为 T2_」的节点（先子后父），最后删根。 */
    private void cleanupTmp() {
        for (int i = 0; i < 10; i++) {
            var tmp = organizationRepository.findAll().stream()
                    .filter(o -> o.getOrgCode() != null && o.getOrgCode().startsWith("T2_"))
                    .toList();
            if (tmp.isEmpty()) return;
            var withT2Parent = tmp.stream()
                    .filter(o -> o.getParent() != null && o.getParent().getOrgCode() != null
                            && o.getParent().getOrgCode().startsWith("T2_"))
                    .toList();
            if (withT2Parent.isEmpty()) {
                tmp.forEach(organizationRepository::delete); // 已无 T2_ 内部引用，可删根
                return;
            }
            withT2Parent.forEach(organizationRepository::delete);
        }
    }

    @Test
    @Order(1)
    void a_tree_departments_with_member_count() {
        loginAs("admin");
        ApiResponse<List<Map<String, Object>>> r = organizationController.tree();
        var tech = r.getData().stream()
                .filter(n -> "TECH".equals(n.get("orgCode"))).findFirst().orElseThrow();
        long expect = userRepository.countByOrgIdAndStatus(((Number) tech.get("id")).longValue(), UserStatus.ACTIVE);
        assertEquals(expect, ((Number) tech.get("memberCount")).longValue());
        assertNotNull(tech.get("children"), "children 应为 List");
    }

    @Test
    @Order(2)
    void b_create_and_duplicate_code() {
        loginAs("admin");
        OrganizationRequest req = new OrganizationRequest();
        req.setOrgName("T2_测试部");
        assertEquals(200, organizationController.create(req).getCode());
        OrganizationRequest dup = new OrganizationRequest();
        dup.setOrgName("T2_测试部");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationController.create(dup));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("已存在"));
    }

    @Test
    @Order(3)
    void c_update_cycle_rejected() {
        loginAs("admin");
        var a = createTmp("T2_环A");
        var b = createTmp("T2_环B");
        OrganizationRequest ok = new OrganizationRequest();
        ok.setParentId(b.getId());
        assertEquals(200, organizationController.update(a.getId(), ok).getCode());
        OrganizationRequest bad = new OrganizationRequest();
        bad.setParentId(a.getId());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationController.update(b.getId(), bad));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("环"));
    }

    @Test
    @Order(4)
    void d_delete_protection() {
        loginAs("admin");
        var dept = createTmp("T2_保护部");
        var child = createTmp("T2_保护子部");
        OrganizationRequest mv = new OrganizationRequest();
        mv.setParentId(dept.getId());
        organizationController.update(child.getId(), mv);
        ResponseStatusException sub = assertThrows(ResponseStatusException.class,
                () -> organizationController.delete(dept.getId()));
        assertTrue(sub.getReason().contains("子部门"));
        organizationController.delete(child.getId());
        var fin = userRepository.findByUsername("finance").orElseThrow();
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        fin.setOrg(dept);
        userRepository.save(fin);
        try {
            ResponseStatusException em = assertThrows(ResponseStatusException.class,
                    () -> organizationController.delete(dept.getId()));
            assertTrue(em.getReason().contains("在职员工"));
        } finally {
            fin.setOrg(tech);
            userRepository.save(fin);
        }
    }

    @Test
    @Order(5)
    void e_permission_employee_403_admin_ok() {
        loginAs("employee");
        OrganizationRequest req = new OrganizationRequest();
        req.setOrgName("T2_权限部");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationController.create(req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        loginAs("admin");
        assertEquals(200, organizationController.create(req).getCode());
    }

    @Test
    @Order(6)
    void f_member_count_excludes_disabled() {
        loginAs("admin");
        var dept = createTmp("T2_计数部");
        var tech = organizationRepository.findByOrgCode("TECH").orElseThrow();
        var fin = userRepository.findByUsername("finance").orElseThrow();
        fin.setOrg(dept);
        userRepository.save(fin);
        try {
            assertEquals(1, userRepository.countByOrgIdAndStatus(dept.getId(), UserStatus.ACTIVE));
            userController.disableUser(fin.getId());
            var node = findNode(organizationController.tree().getData(), dept.getId());
            assertNotNull(node);
            assertEquals(0L, ((Number) node.get("memberCount")).longValue());
        } finally {
            fin.setStatus(UserStatus.ACTIVE);
            fin.setOrg(tech);
            userRepository.save(fin);
        }
    }

    @org.junit.jupiter.api.AfterAll
    @Test
    @Order(7)
    void g_update_code_change_rejected() {
        loginAs("admin");
        var o = createTmp("T2_改码");
        OrganizationRequest req = new OrganizationRequest();
        req.setOrgCode("CHANGED-CODE");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> organizationController.update(o.getId(), req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), "code 创建后不可改（引用保护）");
    }

    @Test
    @Order(8)
    void h_promote_to_top_level_via_clear_parent() {
        loginAs("admin");
        var parent = createTmp("T2_上级");
        var child = createTmp("T2_下级");
        child.setParent(parent);
        child = organizationRepository.save(child);

        var before = findNode(organizationController.tree().getData(), child.getId());
        assertEquals(parent.getId(), ((Number) before.get("parentId")).longValue());

        OrganizationRequest req = new OrganizationRequest();
        req.setClearParent(true);
        var r = organizationController.update(child.getId(), req);
        assertEquals(200, r.getCode());
        assertNull(r.getData().get("parentId"), "清除后应为顶级（parentId null）");
        var after = findNode(organizationController.tree().getData(), child.getId());
        assertNull(after.get("parentId"), "树中应为顶级节点");
    }

    @AfterEach
    void cleanup() {
        cleanupTmp();
    }
}
