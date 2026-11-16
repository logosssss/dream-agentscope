-- 审批软退役硬删：去掉旧审批单与审批通知表；清理 APPROVAL 类型待办历史行。
-- ASK 权威在进程内 PendingAsk；CONFIRM/TASK 待办保留。见 docs/approval-hard-delete-落地.md。

DELETE FROM user_todo WHERE todo_type = 'APPROVAL';

DROP TABLE IF EXISTS approval_notification;

DROP TABLE IF EXISTS approval_record;
