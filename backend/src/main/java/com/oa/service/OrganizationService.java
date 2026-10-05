package com.oa.service;

import com.oa.dto.OrganizationRequest;
import com.oa.entity.Organization;
import com.oa.entity.User;
import com.oa.enums.EnableStatus;
import com.oa.enums.UserStatus;
import com.oa.repository.OrganizationRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门管理（SY-02）：部门树（人数实时派生）+ 创建/编辑/删除（删除保护）。
 * 人数 = 该部门直属在职（ACTIVE）员工数，查询时派生，不做冗余计数。
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    /** 部门树：任意登录用户可读（部门下拉/假期管理复用）。节点含 memberCount 与嵌套 children。 */
    public List<Map<String, Object>> tree() {
        List<Organization> depts = organizationRepository.findAll().stream()
                .filter(o -> "DEPT".equals(o.getOrgType()))
                .sorted((a, b) -> {
                    int sa = a.getSortOrder() == null ? 0 : a.getSortOrder();
                    int sb = b.getSortOrder() == null ? 0 : b.getSortOrder();
                    return sa != sb ? Integer.compare(sa, sb) : Long.compare(a.getId(), b.getId());
                })
                .toList();
        // 一次取全部在职员工按部门计数（避免 N+1）
        Map<Long, Long> activeCount = new LinkedHashMap<>();
        for (User u : userRepository.findByStatus(UserStatus.ACTIVE)) {
            if (u.getOrg() != null) {
                activeCount.merge(u.getOrg().getId(), 1L, Long::sum);
            }
        }
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Organization d : depts) {
            nodes.put(d.getId(), nodeOf(d, activeCount));
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Organization d : depts) {
            Map<String, Object> node = nodes.get(d.getId());
            if (d.getParent() == null || !nodes.containsKey(d.getParent().getId())) {
                roots.add(node);
            } else {
                ((List<Map<String, Object>>) nodes.get(d.getParent().getId()).get("children")).add(node);
            }
        }
        return roots;
    }

    private Map<String, Object> nodeOf(Organization d, Map<Long, Long> activeCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("orgCode", d.getOrgCode());
        m.put("orgName", d.getOrgName());
        m.put("parentId", d.getParent() == null ? null : d.getParent().getId());
        m.put("sortOrder", d.getSortOrder());
        m.put("status", d.getStatus() == null ? null : d.getStatus().name());
        m.put("memberCount", activeCount.getOrDefault(d.getId(), 0L));
        m.put("children", new ArrayList<Map<String, Object>>());
        return m;
    }

    public Organization create(OrganizationRequest req) {
        String code = req.getOrgCode() == null || req.getOrgCode().isBlank() ? req.getOrgName().trim() : req.getOrgCode().trim();
        if (organizationRepository.findByOrgCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门代码已存在: " + code);
        }
        if (req.getParentId() != null && organizationRepository.findById(req.getParentId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级部门不存在");
        }
        Organization o = new Organization();
        o.setOrgName(req.getOrgName().trim());
        o.setOrgCode(code);
        o.setOrgType("DEPT");
        o.setSortOrder(req.getSortOrder());
        o.setStatus(EnableStatus.ENABLED);
        if (req.getParentId() != null) {
            o.setParent(organizationRepository.findById(req.getParentId()).orElseThrow());
        }
        return organizationRepository.save(o);
    }

    public Organization update(Long id, OrganizationRequest req) {
        Organization o = requireOrg(id);
        if (req.getOrgName() != null && !req.getOrgName().isBlank()) {
            o.setOrgName(req.getOrgName().trim());
        }
        if (req.getOrgCode() != null && !req.getOrgCode().isBlank()
                && !req.getOrgCode().trim().equals(o.getOrgCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门代码创建后不可修改（引用保护）");
        }
        if (req.getSortOrder() != null) {
            o.setSortOrder(req.getSortOrder());
        }
        if (req.getParentId() != null) {
            if (req.getParentId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门树存在环：上级不能是自己");
            }
            Organization parent = organizationRepository.findById(req.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "上级部门不存在"));
            if (isDescendant(parent, id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部门树存在环：上级不能是自己的后代部门");
            }
            o.setParent(parent);
        } else if (Boolean.TRUE.equals(req.getClearParent())) {
            o.setParent(null);
        }
        return organizationRepository.save(o);
    }

    /** 从 candidate 上溯父链，若遇到 orgId 则为其后代。 */
    private boolean isDescendant(Organization candidate, Long orgId) {
        Organization cur = candidate;
        int guard = 0;
        while (cur != null && guard++ < 100) {
            if (cur.getId().equals(orgId)) return true;
            cur = cur.getParent();
        }
        return false;
    }

    public void delete(Long id) {
        Organization o = requireOrg(id);
        long children = organizationRepository.findByParentId(id).size();
        if (children > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "该部门下仍有子部门，请先删除子部门");
        }
        long active = userRepository.countByOrgIdAndStatus(id, UserStatus.ACTIVE);
        if (active > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "该部门仍有 " + active + " 名在职员工，请先调整部门归属");
        }
        organizationRepository.delete(o);
    }

    private Organization requireOrg(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "部门不存在"));
    }
}
