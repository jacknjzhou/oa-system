-- 模板可“表单已发布、流程未设计”双态（bpmn_xml 允许为空）
ALTER TABLE process_definition MODIFY COLUMN bpmn_xml TEXT NULL;
