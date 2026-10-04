package com.oa.service;

import com.oa.dto.ApprovalTypeDTO;
import com.oa.dto.ApprovalTypeRequest;
import com.oa.entity.ApprovalType;
import com.oa.entity.ProcessDefinition;
import com.oa.repository.ApprovalTypeRepository;
import com.oa.repository.ProcessDefinitionRepository;
import com.oa.repository.ProcessInstanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 审批类型管理（P2-1a）：CRUD + 启停；被实例引用（business_type=code）的类型禁止删除。
 */
@Service
public class ApprovalTypeService {

    @Autowired
    private ApprovalTypeRepository approvalTypeRepository;

    @Autowired
    private ProcessDefinitionRepository definitionRepository;

    @Autowired
    private ProcessInstanceRepository instanceRepository;

    @Transactional(readOnly = true)
    public List<ApprovalTypeDTO> listAll() {
        return approvalTypeRepository.findAllByOrderByWeightAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalTypeDTO> listEnabled() {
        return approvalTypeRepository.findByEnabledOrderByWeightAscIdAsc(true).stream().map(this::toDto).toList();
    }

    @Transactional
    public ApprovalTypeDTO create(ApprovalTypeRequest req) {
        if (approvalTypeRepository.findByCode(req.getCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类型代码已存在：" + req.getCode());
        }
        ProcessDefinition def = definitionRepository.findById(req.getDefId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联流程模板不存在"));
        ApprovalType type = new ApprovalType();
        type.setCode(req.getCode());
        type.setName(req.getName());
        type.setCategory(req.getCategory());
        type.setIcon(req.getIcon());
        type.setDescription(req.getDescription());
        type.setWeight(req.getWeight() != null ? req.getWeight() : 0);
        type.setDef(def);
        type.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        return toDto(approvalTypeRepository.save(type));
    }

    @Transactional
    public ApprovalTypeDTO update(Long id, ApprovalTypeRequest req) {
        ApprovalType type = load(id);
        if (!type.getCode().equals(req.getCode())
                && approvalTypeRepository.findByCode(req.getCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类型代码已存在：" + req.getCode());
        }
        type.setCode(req.getCode());
        type.setName(req.getName());
        type.setCategory(req.getCategory());
        type.setIcon(req.getIcon());
        type.setDescription(req.getDescription());
        if (req.getWeight() != null) {
            type.setWeight(req.getWeight());
        }
        if (req.getDefId() != null) {
            ProcessDefinition def = definitionRepository.findById(req.getDefId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联流程模板不存在"));
            type.setDef(def);
        }
        if (req.getEnabled() != null) {
            type.setEnabled(req.getEnabled());
        }
        return toDto(approvalTypeRepository.save(type));
    }

    @Transactional
    public void delete(Long id) {
        ApprovalType type = load(id);
        // 启用中的类型被实例引用时禁止删除（防发起入口消失）；停用后允许删除
        //（历史实例保留 code 字符串，前端名称解析回退为 code）
        if (Boolean.TRUE.equals(type.getEnabled())) {
            long inUse = instanceRepository.countByBusinessType(type.getCode());
            if (inUse > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "该类型下还有 " + inUse + " 个流程实例，请先停用再删除");
            }
        }
        approvalTypeRepository.delete(type);
    }

    private ApprovalType load(Long id) {
        return approvalTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审批类型不存在"));
    }

    private ApprovalTypeDTO toDto(ApprovalType type) {
        ApprovalTypeDTO dto = new ApprovalTypeDTO();
        dto.setId(type.getId());
        dto.setCode(type.getCode());
        dto.setName(type.getName());
        dto.setCategory(type.getCategory());
        dto.setIcon(type.getIcon());
        dto.setDescription(type.getDescription());
        dto.setWeight(type.getWeight());
        dto.setDefId(type.getDef() != null ? type.getDef().getId() : null);
        dto.setDefName(type.getDef() != null ? type.getDef().getName() : null);
        dto.setEnabled(type.getEnabled());
        return dto;
    }
}
