import { useEffect, useRef } from 'react'
// bpmn-js 样式
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'

interface BpmnViewerProps {
  xml: string
  /** 已完成节点（绿色高亮） */
  completedActivityIds?: string[]
  /** 当前节点（蓝色高亮） */
  currentActivityIds?: string[]
  /** 已驳回节点（红色高亮） */
  rejectedActivityIds?: string[]
  className?: string
}

/**
 * BPMN 流程图查看器：
 * 封装 bpmn-js NavigatedViewer，根据节点状态追加自定义高亮 CSS 类
 * （.highlight-done / .highlight-current / .highlight-rejected，见 styles/index.css）
 */
export default function BpmnViewer({
  xml,
  completedActivityIds = [],
  currentActivityIds = [],
  rejectedActivityIds = [],
  className = 'h-[420px] w-full',
}: BpmnViewerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  // bpmn-js 的 get() 服务定位为动态字符串，类型系统无法表达，使用 any
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const viewerRef = useRef<any>(null)
  /** 已打上的 marker 列表，便于依赖变化时清理 */
  const appliedMarkersRef = useRef<Array<{ id: string; cls: string }>>([])

  useEffect(() => {
    if (!containerRef.current) return
    const viewer = new NavigatedViewer({ container: containerRef.current })
    viewerRef.current = viewer
    return () => {
      viewer.destroy()
      viewerRef.current = null
      appliedMarkersRef.current = []
    }
  }, [])

  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer || !xml) return
    let cancelled = false

    viewer
      .importXML(xml)
      .then(() => {
        if (cancelled) return
        const canvas = viewer.get('canvas')
        const elementRegistry = viewer.get('elementRegistry')
        canvas.zoom('fit-viewport', 'auto')

        appliedMarkersRef.current = []
        const applyMarkers = (ids: string[], cls: string) => {
          ids.forEach((id) => {
            // 仅对画布中真实存在的元素打标，避免脏 id 报错
            if (elementRegistry.get(id)) {
              canvas.addMarker(id, cls)
              appliedMarkersRef.current.push({ id, cls })
            }
          })
        }
        applyMarkers(completedActivityIds, 'highlight-done')
        applyMarkers(currentActivityIds, 'highlight-current')
        applyMarkers(rejectedActivityIds, 'highlight-rejected')
      })
      .catch((err: Error) => {
        if (!cancelled) {
          // 渲染失败时静默降级为空画布，错误由外层数据加载提示兜底
          console.error('BPMN 流程图渲染失败', err)
        }
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [xml, completedActivityIds.join(','), currentActivityIds.join(','), rejectedActivityIds.join(',')])

  return (
    <div className={`overflow-hidden rounded-lg bg-slate-50 dark:bg-slate-900/60 ${className}`}>
      <div ref={containerRef} className="h-full w-full" />
    </div>
  )
}
