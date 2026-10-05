package com.oa.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

    /** BPMN 2.0 模型命名空间。 */
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    public Document parse(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 命名空间感知：保证 getLocalName 对 bpmn: 前缀/默认命名空间都返回局部名
            factory.setNamespaceAware(true);
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
        List<Element> processes = bpmnElements(parse(bpmnXml), "process");
        if (processes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BPMN XML 缺少 process 元素");
        }
        return processes.get(0).getAttribute("id");
    }

    /** 提取全部用户任务节点（id + 名称），驳回目标节点、节点名展示均基于此。 */
    public List<UserTaskInfo> extractUserTasks(String bpmnXml) {
        List<UserTaskInfo> result = new ArrayList<>();
        for (Element el : bpmnElements(parse(bpmnXml), "userTask")) {
            result.add(new UserTaskInfo(el.getAttribute("id"), el.getAttribute("name")));
        }
        return result;
    }

    /**
     * 按局部名收集元素（兼容 bpmn: 前缀 / 默认命名空间 / 无命名空间）。
     * {@code getElementsByTagName} 按限定名匹配，无法识别带前缀的 XML。
     */
    private List<Element> bpmnElements(Document doc, String localName) {
        List<Element> out = new ArrayList<>();
        collect(doc.getDocumentElement(), localName, out);
        return out;
    }

    private void collect(Node node, String localName, List<Element> out) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element el = (Element) node;
        String ns = el.getNamespaceURI();
        if (localName.equals(el.getLocalName())
                && (ns == null || ns.isEmpty() || BPMN_NS.equals(ns))) {
            out.add(el);
        }
        for (Node child = el.getFirstChild(); child != null; child = child.getNextSibling()) {
            collect(child, localName, out);
        }
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
