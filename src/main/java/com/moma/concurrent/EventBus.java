package com.moma.concurrent;

import com.moma.di.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 事件总线。组件间松耦合通信。
 * 支持同步/异步分发。
 */
@Component
public class EventBus {

    private static final Logger LOG = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

    /**
     * 订阅指定类型的事件。
     *
     * @param eventType 事件类型
     * @param handler   事件处理器
     * @param <T>       事件类型泛型
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> handlers = subscribers.computeIfAbsent(
            eventType, k -> new CopyOnWriteArrayList<>());
        handlers.add((Consumer<?>) handler);
        LOG.debug("事件订阅: type={}, 当前订阅者数={}", eventType.getSimpleName(), handlers.size());
    }

    /**
     * 同步发布事件。所有订阅者在当前线程中依次执行。
     *
     * @param event 事件对象
     * @param <T>   事件类型泛型
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        Class<?> eventType = event.getClass();
        List<Consumer<?>> handlers = subscribers.get(eventType);
        if (handlers == null || handlers.isEmpty()) {
            LOG.trace("事件发布无订阅者: type={}", eventType.getSimpleName());
            return;
        }

        LOG.debug("事件发布: type={}, 订阅者数={}", eventType.getSimpleName(), handlers.size());
        for (Consumer<?> handler : handlers) {
            try {
                ((Consumer<T>) handler).accept(event);
            } catch (Exception e) {
                LOG.error("事件处理器执行异常: type={}, error={}", eventType.getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 异步发布事件。所有订阅者在指定执行器中执行。
     *
     * @param event    事件对象
     * @param executor 执行器（通常传线程池）
     * @param <T>      事件类型泛型
     */
    @SuppressWarnings("unchecked")
    public <T> void publishAsync(T event, Executor executor) {
        Class<?> eventType = event.getClass();
        List<Consumer<?>> handlers = subscribers.get(eventType);
        if (handlers == null || handlers.isEmpty()) {
            LOG.trace("事件异步发布无订阅者: type={}", eventType.getSimpleName());
            return;
        }

        LOG.debug("事件异步发布: type={}, 订阅者数={}", eventType.getSimpleName(), handlers.size());
        for (Consumer<?> handler : handlers) {
            executor.execute(() -> {
                try {
                    ((Consumer<T>) handler).accept(event);
                } catch (Exception e) {
                    LOG.error("事件异步处理器执行异常: type={}, error={}",
                        eventType.getSimpleName(), e.getMessage(), e);
                }
            });
        }
    }

    /**
     * 清空所有订阅。
     */
    public void clear() {
        subscribers.clear();
        LOG.info("事件总线订阅已清空");
    }

    /**
     * 获取当前订阅数量。
     */
    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(List::size).sum();
    }
}
