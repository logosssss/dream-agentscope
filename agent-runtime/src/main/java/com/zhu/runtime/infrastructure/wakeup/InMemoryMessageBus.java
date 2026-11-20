package com.zhu.runtime.infrastructure.wakeup;

import io.agentscope.harness.agent.bus.BusEntry;
import io.agentscope.harness.agent.bus.MessageBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 运行层：进程内 {@link MessageBus}，供 Wakeup 窄专题使用。
 * 不落盘、不跨副本；满足 enqueueWakeup / subscribeWakeup 默认实现所需的 queue 与 pub/sub。
 * 见 docs/wakeup-落地.md。
 */
public final class InMemoryMessageBus implements MessageBus {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<BusEntry>> queues = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<BusEntry>> logs = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Sinks.Many<Map<String, Object>>> topics = new ConcurrentHashMap<>();

    private volatile boolean closed;

    /** 入队；返回条目 id。 */
    @Override
    public Mono<String> queuePush(String key, Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
            ensureOpen();
            String id = UUID.randomUUID().toString();
            Map<String, Object> body = payload == null ? Map.of() : Map.copyOf(payload);
            queue(key).add(new BusEntry(id, body));
            return id;
        });
    }

    /** 出队最多 {@code max} 条；空队列返回空列表。 */
    @Override
    public Mono<List<BusEntry>> queueDrain(String key, int max) {
        return Mono.fromCallable(() -> {
            ensureOpen();
            ConcurrentLinkedQueue<BusEntry> q = queues.get(normalize(key));
            if (q == null || q.isEmpty() || max <= 0) {
                return List.of();
            }
            List<BusEntry> out = new ArrayList<>(Math.min(max, q.size()));
            for (int i = 0; i < max; i++) {
                BusEntry entry = q.poll();
                if (entry == null) {
                    break;
                }
                out.add(entry);
            }
            return List.copyOf(out);
        });
    }

    /** 删除整条队列。 */
    @Override
    public Mono<Void> queueDelete(String key) {
        return Mono.fromRunnable(() -> {
            ensureOpen();
            queues.remove(normalize(key));
        });
    }

    /** 队列是否非空。 */
    @Override
    public Mono<Boolean> queuePeek(String key) {
        return Mono.fromCallable(() -> {
            ensureOpen();
            ConcurrentLinkedQueue<BusEntry> q = queues.get(normalize(key));
            return q != null && !q.isEmpty();
        });
    }

    /** 追加日志条目；超长时丢最旧。 */
    @Override
    public Mono<String> logAppend(String key, Map<String, Object> payload, int maxLen) {
        return Mono.fromCallable(() -> {
            ensureOpen();
            String id = UUID.randomUUID().toString();
            Map<String, Object> body = payload == null ? Map.of() : Map.copyOf(payload);
            CopyOnWriteArrayList<BusEntry> list = logs.computeIfAbsent(normalize(key), k -> new CopyOnWriteArrayList<>());
            list.add(new BusEntry(id, body));
            int keep = Math.max(1, maxLen);
            while (list.size() > keep) {
                list.remove(0);
            }
            return id;
        });
    }

    /** 读取日志；{@code afterId} 为空从开头。 */
    @Override
    public Mono<List<BusEntry>> logRead(String key, String afterId, int max) {
        return Mono.fromCallable(() -> {
            ensureOpen();
            CopyOnWriteArrayList<BusEntry> list = logs.get(normalize(key));
            if (list == null || list.isEmpty() || max <= 0) {
                return List.of();
            }
            boolean skip = afterId != null && !afterId.isBlank();
            List<BusEntry> out = new ArrayList<>();
            for (BusEntry entry : list) {
                if (skip) {
                    if (Objects.equals(entry.entryId(), afterId)) {
                        skip = false;
                    }
                    continue;
                }
                out.add(entry);
                if (out.size() >= max) {
                    break;
                }
            }
            return List.copyOf(out);
        });
    }

    /** 清空日志键。 */
    @Override
    public Mono<Void> logTrim(String key) {
        return Mono.fromRunnable(() -> {
            ensureOpen();
            logs.remove(normalize(key));
        });
    }

    /** 发布到主题；无订阅也不丢（multicast）。 */
    @Override
    public Mono<Void> publish(String topic, Map<String, Object> payload) {
        return Mono.fromRunnable(() -> {
            ensureOpen();
            Map<String, Object> body = payload == null ? Map.of() : Map.copyOf(payload);
            sink(topic).tryEmitNext(body);
        });
    }

    /** 订阅主题；后到的订阅收后续消息。 */
    @Override
    public Flux<Map<String, Object>> subscribe(String topic) {
        ensureOpen();
        return sink(topic).asFlux();
    }

    /** 关闭后拒绝新操作；尽量完成已有 sink。 */
    @Override
    public void close() {
        closed = true;
        topics.values().forEach(Sinks.Many::tryEmitComplete);
        topics.clear();
        queues.clear();
        logs.clear();
    }

    private ConcurrentLinkedQueue<BusEntry> queue(String key) {
        return queues.computeIfAbsent(normalize(key), k -> new ConcurrentLinkedQueue<>());
    }

    private Sinks.Many<Map<String, Object>> sink(String topic) {
        return topics.computeIfAbsent(
                normalize(topic),
                k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private static String normalize(String key) {
        return key == null ? "" : key;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("InMemoryMessageBus is closed");
        }
    }
}
