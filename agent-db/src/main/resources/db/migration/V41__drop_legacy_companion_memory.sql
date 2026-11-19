-- Memory 情景/画像硬删：去掉遗留表。跨会话权威是工作区 MEMORY.md。
-- Milvus episodic_memory 由代码停建，不在此 DROP 集合。见 docs/memory-hard-delete-落地.md。

DROP TABLE IF EXISTS episodic_memory;

DROP TABLE IF EXISTS user_profile;
