-- 可视化流程设计器的流程规格 JSON（FlowDesigner 状态持久化）
ALTER TABLE process_definition ADD COLUMN flow_spec TEXT NULL;
