package com.oa.bpmn;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * BPMN DI 自愈：bpmn-js（NavigatedViewer）渲染依赖 &lt;bpmndi:BPMNDiagram&gt;，
 * 缺少 DI 的 XML 导入成功但画布空白（表现为“流程图无法加载”）。
 *
 * 本工具检测 XML 是否缺少 DI；缺失则按节点文档顺序生成一条横向链式布局的
 * BPMNPlane（事件 36×36 / 任务 120×80 / 网关 50×50），并注入 definitions 结束标签前。
 * 已含 DI 或解析失败的 XML 原样返回（绝不破坏可部署的 XML）。
 */
public final class BpmnDiHealer {

    private static final Set<String> SHAPE_LOCAL_NAMES = Set.of(
            "startEvent", "endEvent", "intermediateCatchEvent", "intermediateThrowEvent",
            "userTask", "serviceTask", "scriptTask", "sendTask", "receiveTask", "businessRuleTask",
            "manualTask", "exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway");

    private BpmnDiHealer() {
    }

    /** XML 是否缺少 DI 段。 */
    public static boolean hasDi(String xml) {
        return xml != null && xml.contains("BPMNDiagram");
    }

    /**
     * 保证 XML 含 DI：已含则原样返回；缺失则生成注入。
     * 解析失败 / 无 process 时原样返回（调用方按原 XML 处理）。
     */
    public static String ensureDi(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        if (hasDi(xml)) {
            return xml;
        }
        try {
            Document doc = parse(xml);
            Element definitions = doc.getDocumentElement();
            Element process = firstChild(definitions, "process");
            if (process == null) {
                return xml;
            }
            String processId = process.getAttribute("id");
            if (processId.isBlank()) {
                return xml;
            }

            // 节点（文档顺序）+ 包围盒
            List<Element> nodes = new ArrayList<>();
            for (Node child = process.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE && SHAPE_LOCAL_NAMES.contains(localName(child))) {
                    nodes.add((Element) child);
                }
            }
            if (nodes.isEmpty()) {
                return xml;
            }

            int x = 60;
            int y = 160;
            StringBuilder shapes = new StringBuilder();
            StringBuilder edges = new StringBuilder();
            Map<String, int[]> centerById = new java.util.LinkedHashMap<>();
            for (Element el : nodes) {
                String id = el.getAttribute("id");
                if (id.isBlank()) {
                    continue;
                }
                String ln = localName(el);
                int w;
                int h;
                if (ln.endsWith("Event")) {
                    w = 36;
                    h = 36;
                } else if (ln.endsWith("Gateway")) {
                    w = 50;
                    h = 50;
                } else {
                    w = 120;
                    h = 80;
                }
                centerById.put(id, new int[]{x + w / 2, y + h / 2});
                // 形状 id 必须与流程元素 id 不同（XML ID 全局唯一，Flowable 会校验）
                shapes.append("      <bpmndi:BPMNShape id=\"").append(esc(id)).append("_di")
                        .append("\" bpmnElement=\"").append(esc(id)).append("\">\n")
                        .append("        <dc:Bounds x=\"").append(x).append("\" y=\"").append(y)
                        .append("\" width=\"").append(w).append("\" height=\"").append(h).append("\" />\n")
                        .append("      </bpmndi:BPMNShape>\n");
                x += w + 60;
            }
            for (Node child = process.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() != Node.ELEMENT_NODE || !"sequenceFlow".equals(localName(child))) {
                    continue;
                }
                Element flow = (Element) child;
                String id = flow.getAttribute("id");
                String src = flow.getAttribute("sourceRef");
                String tgt = flow.getAttribute("targetRef");
                int[] s = centerById.get(src);
                int[] t = centerById.get(tgt);
                if (id == null || id.isBlank() || s == null || t == null) {
                    continue;
                }
                edges.append("      <bpmndi:BPMNEdge id=\"").append(esc(id)).append("_di\" bpmnElement=\"")
                        .append(esc(id)).append("\">\n")
                        .append("        <di:waypoint x=\"").append(s[0]).append("\" y=\"").append(s[1]).append("\" />\n")
                        .append("        <di:waypoint x=\"").append(t[0]).append("\" y=\"").append(t[1]).append("\" />\n")
                        .append("      </bpmndi:BPMNEdge>\n");
            }

            String di = "<bpmndi:BPMNDiagram id=\"DI_" + esc(processId) + "\"\n"
                    + "     xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"\n"
                    + "     xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"\n"
                    + "     xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\">\n"
                    + "  <bpmndi:BPMNPlane id=\"Plane_" + esc(processId) + "\" bpmnElement=\"" + esc(processId) + "\">\n"
                    + shapes
                    + edges
                    + "  </bpmndi:BPMNPlane>\n"
                    + "</bpmndi:BPMNDiagram>\n";

            // DOM 规范：默认命名空间 getPrefix() 返回 null
            String closePrefix = process.getPrefix() == null ? "" : process.getPrefix();
            String closeTag = closePrefix.isEmpty() ? "</definitions>" : "</" + closePrefix + ":definitions>";
            int idx = xml.lastIndexOf(closeTag);
            if (idx < 0) {
                return xml;
            }
            // bpmndi/dc/di 命名空间在 DI 根元素上自声明，definitions 无需改动
            return xml.substring(0, idx) + di + xml.substring(idx);
        } catch (Exception e) {
            return xml;
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String localName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }

    private static Element firstChild(Element parent, String local) {
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && local.equals(localName(n))) {
                return (Element) n;
            }
        }
        return null;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
