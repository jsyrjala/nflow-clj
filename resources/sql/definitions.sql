-- :name insert-definition! :insert
-- :doc
insert into nflow_workflow_definition(type, definition_sha1, definition, modified_by, executor_group)
values (:type, :definition_sha, :definition, :modified_by, :executor_group);

-- :name update-definition! :!
-- :doc
update nflow_workflow_definition
set definition = :definition, modified_by = :modified_by, definition_sha1 = :definition_sha
where type = :type and executor_group = :executor_group and definition_sha1 <> :definition_sha;

-- :name query-definitions :*
-- :doc
select * from nflow_workflow_definition where executor_group = :executor_group;
