package com.oa.service;

import com.oa.entity.Permission;
import com.oa.entity.PermissionGroup;
import com.oa.entity.User;
import com.oa.repository.PermissionGroupRepository;
import com.oa.repository.PermissionRepository;
import com.oa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 权组与权限（SY-03）。 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionGroupRepository groupRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public List<PermissionGroup> listGroups() {
        return groupRepository.findAll();
    }

    public List<Permission> listPermissions(String groupName) {
        List<Permission> all = permissionRepository.findAllByOrderByIdAsc();
        if (groupName == null || groupName.isBlank()) {
            return all;
        }
        return all.stream().filter(p -> p.getGroupName().equals(groupName)).toList();
    }

    public List<String> permissionCodes(Long userId) {
        return groupRepository.findByMembersId(userId).stream()
                .filter(g -> Boolean.TRUE.equals(g.getEnabled()))
                .flatMap(g -> g.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public PermissionGroup createGroup(String code, String name) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编码与名称必填");
        }
        String c = code.trim().toUpperCase();
        if (groupRepository.findByCode(c).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编码已存在: " + c);
        }
        PermissionGroup g = new PermissionGroup();
        g.setCode(c);
        g.setName(name.trim());
        return groupRepository.save(g);
    }

    @Transactional
    public PermissionGroup assignPermissions(Long groupId, Set<Long> permissionIds) {
        PermissionGroup g = requireGroup(groupId);
        g.getPermissions().clear();
        if (permissionIds != null && !permissionIds.isEmpty()) {
            g.getPermissions().addAll(permissionRepository.findByIdInOrderByIdAsc(
                    permissionIds.stream().sorted().toList()));
        }
        return groupRepository.save(g);
    }

    @Transactional
    public PermissionGroup assignMembers(Long groupId, Set<Long> userIds) {
        PermissionGroup g = requireGroup(groupId);
        g.getMembers().clear();
        if (userIds != null && !userIds.isEmpty()) {
            List<User> users = userRepository.findAllById(userIds);
            if (users.size() != userIds.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在不存在的用户");
            }
            g.getMembers().addAll(users);
        }
        return groupRepository.save(g);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        groupRepository.deleteById(groupId);
    }

    private PermissionGroup requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "权组不存在"));
    }
}
