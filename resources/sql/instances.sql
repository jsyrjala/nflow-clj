-- :name insert-instance! :insert
-- :doc Insert new workflow instance
insert into nflow_workflow(type, root_workflow_id, parent_workflow_id, parent_action_id, business_key, external_id,
  executor_group, status, state, state_text, next_activation)
  values(:type, :root_workflow_id, :parent_workflow_id, :parent_action_id, :business_key, :external_id,
        :executor_group, :status, :state, :state_text, :next_activation);

-- :name insert-action! :insert
-- :doc
insert into nflow_workflow_action(workflow_id, executor_id, type, state, state_text, retry_no, execution_start, execution_end)
values (:workflow_id, :executor_id, :type, :state, :state_text, :retry_no, :execution_start, :execution_end);

-- :name insert-state! :insert
-- :doc
insert into nflow_workflow_state(workflow_id, action_id, state_key, state_value)
value (:workflow_id, :action_id, :state_key, :state_value);