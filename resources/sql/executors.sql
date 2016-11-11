-- :name insert-executor! :insert
-- :doc Create new executor
insert into nflow_executor(host, pid, executor_group, active, expires)
values (:host, :pid, :executor_group, current_timestamp, date_add(current_timestamp, interval :expires_in second));

-- :name update-executor-activity! :!
update nflow_executor
set active = current_timestamp, expires = date_add(current_timestamp, interval :expires_in second)
where id = :executor_id;

-- :name query-executors :*
-- :doc TODO
select * from nflow_executor
where executor_group = :executor_group
order by id asc;
