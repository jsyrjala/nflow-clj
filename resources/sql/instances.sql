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

-- :name query-recoverable-instances :*
-- :doc
select id, state from nflow_workflow
where executor_id in (
  select id from nflow_executor
  where executor_group = :executor_group
    and id <> :executor_id
    and expires < current_timestamp);

-- :name query-processable-instances :*
-- :doc
select id, modified
from nflow_workflow
where executor_id is null and status in ( 'created', 'inProgress' )
  and next_activation <= current_timestamp
  and executor_group = :executor_group
order by next_activation asc
limit :limit;


-- :name update-reserve-instance! :! :n
-- :doc
update nflow_workflow
set executor_id = :executor_id, status = 'executing', external_next_activation = null
where id = :id and modified = :modified and executor_id is null;