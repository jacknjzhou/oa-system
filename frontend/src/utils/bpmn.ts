/**
 * BPMN XML 工具函数：
 * - 从 XML 中解析 userTask 节点（用于"驳回到指定节点"下拉）
 * - 同步 process id 与 defKey（后端按 defKey 发起流程）
 * - 生成新建模板的最小初始 BPMN XML
 */

export interface BpmnUserTaskInfo {
  id: string
  name: string
}

function parseXml(xml: string): Document | null {
  try {
    const doc = new DOMParser().parseFromString(xml, 'application/xml')
    if (doc.getElementsByTagName('parsererror').length > 0) return null
    return doc
  } catch {
    return null
  }
}

/** 解析 BPMN XML 中的全部 userTask 节点（id + name） */
export function parseUserTasks(bpmnXml: string): BpmnUserTaskInfo[] {
  const doc = parseXml(bpmnXml)
  if (!doc) return []
  const nodes = doc.getElementsByTagNameNS('*', 'userTask')
  const tasks: BpmnUserTaskInfo[] = []
  for (let i = 0; i < nodes.length; i++) {
    const el = nodes[i]
    const id = el.getAttribute('id') || ''
    if (!id) continue
    const name = el.getAttribute('name') || id
    tasks.push({ id, name })
  }
  return tasks
}

/** 保存前将 process id（及 DI 平面引用）同步为 defKey，保证后端可按 key 发起 */
export function syncProcessId(bpmnXml: string, defKey: string): string {
  if (!defKey) return bpmnXml
  const doc = parseXml(bpmnXml)
  if (!doc) return bpmnXml
  const processes = doc.getElementsByTagNameNS('*', 'process')
  if (processes.length === 0) return bpmnXml
  const process = processes[0]
  const oldId = process.getAttribute('id')
  process.setAttribute('id', defKey)
  if (oldId) {
    const planes = doc.getElementsByTagNameNS('*', 'BPMNPlane')
    for (let i = 0; i < planes.length; i++) {
      if (planes[i].getAttribute('bpmnElement') === oldId) {
        planes[i].setAttribute('bpmnElement', defKey)
      }
    }
  }
  return new XMLSerializer().serializeToString(doc)
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

/**
 * 新建模板的初始 BPMN：
 * 开始 → 经理审批 → 金额网关 ─(>10000)→ 总监审批 ─┐
 *                            └─(<=10000)─────────→ 汇合 → 结束
 */
export function buildInitialBpmnXml(defKey: string, processName: string): string {
  const key = defKey || 'new_process'
  const name = escapeXml(processName || '审批流程')
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  id="Definitions_${escapeXml(key)}"
                  targetNamespace="http://oa.example.com/process">
  <bpmn:process id="${escapeXml(key)}" name="${name}" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_start</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Activity_manager" name="经理审批" flowable:candidateGroups="MANAGER">
      <bpmn:incoming>Flow_start</bpmn:incoming>
      <bpmn:outgoing>Flow_to_gateway</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_amount" name="金额判断">
      <bpmn:incoming>Flow_to_gateway</bpmn:incoming>
      <bpmn:outgoing>Flow_amount_large</bpmn:outgoing>
      <bpmn:outgoing>Flow_amount_small</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Activity_director" name="总监审批" flowable:candidateGroups="ADMIN">
      <bpmn:incoming>Flow_amount_large</bpmn:incoming>
      <bpmn:outgoing>Flow_director_merge</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_merge" name="分支汇合">
      <bpmn:incoming>Flow_amount_small</bpmn:incoming>
      <bpmn:incoming>Flow_director_merge</bpmn:incoming>
      <bpmn:outgoing>Flow_end</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:endEvent id="EndEvent_1" name="结束">
      <bpmn:incoming>Flow_end</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_start" sourceRef="StartEvent_1" targetRef="Activity_manager" />
    <bpmn:sequenceFlow id="Flow_to_gateway" sourceRef="Activity_manager" targetRef="Gateway_amount" />
    <bpmn:sequenceFlow id="Flow_amount_large" name="金额 &gt; 10000" sourceRef="Gateway_amount" targetRef="Activity_director">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">\${amount &gt; 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_amount_small" name="金额 &lt;= 10000" sourceRef="Gateway_amount" targetRef="Gateway_merge">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">\${amount &lt;= 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_director_merge" sourceRef="Activity_director" targetRef="Gateway_merge" />
    <bpmn:sequenceFlow id="Flow_end" sourceRef="Gateway_merge" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${escapeXml(key)}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_manager_di" bpmnElement="Activity_manager">
        <dc:Bounds x="240" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_amount_di" bpmnElement="Gateway_amount">
        <dc:Bounds x="400" y="195" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="388" y="165" width="75" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_director_di" bpmnElement="Activity_director">
        <dc:Bounds x="520" y="60" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_merge_di" bpmnElement="Gateway_merge">
        <dc:Bounds x="660" y="195" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="770" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_start_di" bpmnElement="Flow_start">
        <di:waypoint x="188" y="220" />
        <di:waypoint x="240" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_to_gateway_di" bpmnElement="Flow_to_gateway">
        <di:waypoint x="340" y="220" />
        <di:waypoint x="400" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_amount_large_di" bpmnElement="Flow_amount_large">
        <di:waypoint x="425" y="195" />
        <di:waypoint x="425" y="100" />
        <di:waypoint x="520" y="100" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="438" y="132" width="72" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_amount_small_di" bpmnElement="Flow_amount_small">
        <di:waypoint x="450" y="220" />
        <di:waypoint x="660" y="220" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="505" y="232" width="100" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_director_merge_di" bpmnElement="Flow_director_merge">
        <di:waypoint x="620" y="100" />
        <di:waypoint x="685" y="100" />
        <di:waypoint x="685" y="195" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_end_di" bpmnElement="Flow_end">
        <di:waypoint x="710" y="220" />
        <di:waypoint x="770" y="220" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`
}

/**
 * Flowable 的 bpmn-moddle 扩展描述符（最小集）：
 * 让 bpmn-js 认识 flowable:assignee / flowable:candidateUsers / flowable:candidateGroups 属性，
 * 从而可以通过 modeling.updateProperties 读写并序列化到 XML。
 */
export const flowableModdleDescriptor = {
  name: 'Flowable',
  uri: 'http://flowable.org/bpmn',
  prefix: 'flowable',
  xml: {
    tagAlias: 'lowerCase',
    attrPrefix: '',
  },
  types: [
    {
      name: 'UserTask',
      extends: ['bpmn:UserTask'],
      properties: [
        { name: 'assignee', type: 'String', isAttr: true },
        { name: 'candidateUsers', type: 'String', isAttr: true },
        { name: 'candidateGroups', type: 'String', isAttr: true },
      ],
    },
  ],
}
