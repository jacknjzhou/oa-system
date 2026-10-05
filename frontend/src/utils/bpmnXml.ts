/**
 * 流程设计 spec → BPMN XML 生成器（P2-2 可视化流程设计器）。
 *
 * 生成的结构（线性链）：
 *   start → [serviceTask 主管解析] → userTask... → end
 * - 主管审批：serviceTask(AssignSupervisorDelegate) 前置 + candidateUsers=${supervisorUsername}
 * - 指定用户：candidateUsers="<username>"
 * - 指定角色：candidateGroups="A,B"（单签）；会签/并签 → multiInstance + flowGroups_<id> 变量
 *   （名单由后端发起流程时按 flowSpec 注入，JUEL 无法写列表字面量）
 * - 会签完成条件 = 全部实例完成；并签 = 首个完成即通过
 */
import type { FlowNodeSpec, FlowSpec } from '../types'

const NS =
  'xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"'

function esc(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function nodeUserTaskXml(node: FlowNodeSpec): string {
  const indent = '      '
  let inner = ''
  if (node.approverType === 'supervisor') {
    inner = 'flowable:candidateUsers="${supervisorUsername}"'
  } else if (node.approverType === 'user') {
    inner = `flowable:candidateUsers="${esc(node.username ?? '')}"`
  } else if (node.signMode === 'countersign' || node.signMode === 'cosign') {
    inner = `flowable:candidateGroups="${`fg_${node.id}`}"`
  } else {
    const groups = node.roles.join(',')
    inner = groups ? `flowable:candidateGroups="${esc(groups)}"` : ''
  }

  let mi = ''
  if (node.signMode === 'countersign' || node.signMode === 'cosign') {
    const condition =
      node.signMode === 'countersign'
        ? '${nrOfInstances == nrOfCompletedInstances}'
        : '${nrOfCompletedInstances >= 1}'
    mi = `\n${indent}  <multiInstanceLoopCharacteristics isSequential="false"\n` +
      `${indent}      flowable:collection="\${flowGroups_${node.id}}"\n` +
      `${indent}      flowable:elementVariable="fg_${node.id}">\n` +
      `${indent}    <completionCondition>${condition}</completionCondition>\n` +
      `${indent}  </multiInstanceLoopCharacteristics>`
  }

  return `${indent}<bpmn:userTask id="${esc(node.id)}" name="${esc(node.name)}" ${inner}>${mi}\n    </bpmn:userTask>`
}

interface DiShape {
  id: string
  x: number
  w: number
  h: number
}

const DI_Y = 160
const DI_GAP = 60

/** 链式横向布局的 BPMNDiagram（事件 36×36 / 任务 100×80），与生成链一一对应。 */
function buildDiXml(defKey: string, spec: FlowSpec): string {
  const shapes: DiShape[] = [{ id: 'start', x: 60, w: 36, h: 36 }]
  const edges: Array<[string, string, string]> = []
  let prev = 'start'
  let x = 60

  const put = (id: string, w: number, h: number, from: string) => {
    shapes.push({ id, x, w, h })
    edges.push([`f_${from}_${id}`, from, id])
    x += w + DI_GAP
  }

  for (const node of spec.nodes) {
    if (node.approverType === 'supervisor') {
      put(`prep_${node.id}`, 100, 80, prev)
      prev = `prep_${node.id}`
    }
    put(node.id, 100, 80, prev)
    prev = node.id
  }
  shapes.push({ id: 'end', x, w: 36, h: 36 })
  edges.push([`f_${prev}_end`, prev, 'end'])

  const center = (id: string) => {
    const s = shapes.find((v) => v.id === id)!
    return { x: s.x + s.w / 2, y: DI_Y + s.h / 2 }
  }
  const shapeXml = shapes
    .map(
      (s) =>
        `    <bpmndi:BPMNShape id="${esc(s.id)}_di" bpmnElement="${esc(s.id)}">\n` +
        `      <dc:Bounds x="${s.x}" y="${DI_Y}" width="${s.w}" height="${s.h}"/>\n` +
        `    </bpmndi:BPMNShape>`,
    )
    .join('\n')
  const edgeXml = edges
    .map(([id, from, to]) => {
      const a = center(from)
      const b = center(to)
      return (
        `    <bpmndi:BPMNEdge id="${esc(id)}_di" bpmnElement="${esc(id)}">\n` +
        `      <di:waypoint x="${a.x}" y="${a.y}"/>\n` +
        `      <di:waypoint x="${b.x}" y="${b.y}"/>\n` +
        `    </bpmndi:BPMNEdge>`
      )
    })
    .join('\n')

  return (
    `<bpmndi:BPMNDiagram id="DI_${esc(defKey)}"\n` +
    `    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"\n` +
    `    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"\n` +
    `    xmlns:di="http://www.omg.org/spec/DD/20100524/DI">\n` +
    `  <bpmndi:BPMNPlane id="Plane_${esc(defKey)}" bpmnElement="${esc(defKey)}">\n` +
    shapeXml +
    '\n' +
    edgeXml +
    '\n  </bpmndi:BPMNPlane>\n' +
    '</bpmndi:BPMNDiagram>'
  )
}

/**
 * 由流程设计 spec 生成完整 BPMN XML（含 BPMNDiagram 布局——bpmn-js 渲染依赖 DI）。
 * 无节点时生成「开始 → 结束」最小流程（模板仍可发起，但无审批环节——由前端提示）。
 */
export function buildFlowBpmnXml(defKey: string, name: string, spec: FlowSpec): string {
  const parts: string[] = []
  parts.push(
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<bpmn:definitions ${NS} targetNamespace="http://oa.local/${esc(defKey)}">`,
    `  <bpmn:process id="${esc(defKey)}" name="${esc(name)}" isExecutable="true">`,
    '    <bpmn:startEvent id="start" name="发起"/>',
  )

  let prev = 'start'
  spec.nodes.forEach((node) => {
    if (node.approverType === 'supervisor') {
      const prepId = `prep_${node.id}`
      parts.push(`    <bpmn:sequenceFlow id="f_${prev}_${prepId}" sourceRef="${esc(prev)}" targetRef="${prepId}"/>`)
      parts.push(
        `    <bpmn:serviceTask id="${prepId}" name="解析发起人主管"\n` +
          `                   flowable:delegateExpression="\${assignSupervisorDelegate}"/>`,
      )
      prev = prepId
    }
    parts.push(`    <bpmn:sequenceFlow id="f_${prev}_${node.id}" sourceRef="${esc(prev)}" targetRef="${esc(node.id)}"/>`)
    parts.push(nodeUserTaskXml(node))
    prev = node.id
  })

  parts.push(`    <bpmn:sequenceFlow id="f_${prev}_end" sourceRef="${esc(prev)}" targetRef="end"/>`)
  parts.push('    <bpmn:endEvent id="end" name="结束"/>')
  parts.push('  </bpmn:process>')
  parts.push(buildDiXml(defKey, spec))
  parts.push('</bpmn:definitions>')
  return parts.join('\n')
}
