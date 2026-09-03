package com.oa.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN 2.0 XML 解析工具：DOM 解析，提取流程 id 与用户任务节点。
 */
@Service
public class BpmnXmlService {

    public record UserTaskInfo(String id, String name) {
    }

    public Document parse(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            return doc;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPMN XML 解析失败: " + e.getMessage());
        }
    }

    /** 提取 BPMN 根 process 元素的 id（流程 key）。 */
    public String extractProcessKey(String bpmnXml) {
        NodeList processes = parse(bpmnXml).getElementsByTagName("process");
        if (processes.getLength() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPMN XML 缺少 process 元素");
        }
        return ((Element) processes.item(0)).getAttribute("id");
    }

    /** 提取全部用户任务节点（id + 名称），驳回目标节点、节点名展示均基于此。 */
    public List<UserTaskInfo> extractUserTasks(String bpmnXml) {
        List<UserTaskInfo> result = new ArrayList<>();
        NodeList tasks = parse(bpmnXml).getElementsByTagName("userTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element el = (Element) tasks.item(i);
            result.add(new UserTaskInfo(el.getAttribute("id"), el.getAttribute("name")));
        }
        return result;
    }

    /** id → 名称 的映射（含 userTask；找不到返回 id 本身）。 */
    public Map<String, String> userTaskNameMap(String bpmnXml) {
        Map<String, String> map = new LinkedHashMap<>();
        for (UserTaskInfo t : extractUserTasks(bpmnXml)) {
            map.put(t.id(), t.name() == null || t.name().isBlank() ? t.id() : t.name());
        }
        return map;
    }
}
