-- Advanced RAG：切块正文全文索引。中文依赖 ngram 插件（ngram_token_size 建议 2）。
-- 无 ngram 时本迁移会失败，需先安装并配置后再启动用户进程。

ALTER TABLE kb_chunk
  ADD FULLTEXT KEY ft_content (content) WITH PARSER ngram;
