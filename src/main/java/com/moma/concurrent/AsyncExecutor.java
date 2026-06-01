package com.moma.concurrent;

import com.moma.di.Component;
import com.moma.di.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 异步执行器。提供统一的异步任务提交接口。
 */
@Component
public class AsyncExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncExecutor.class);

    private final ThreadPoolManager threadPoolManager;

    @Inject
    public AsyncExecutor(ThreadPoolManager threadPoolManager) {
        this.threadPoolManager = threadPoolManager;
    }

    /**
     * 提交一个异步任务到指定线程池。
     *
     * @param task     可调用的任务
     * @param poolName 线程池名称 (compute/io/cache/event)
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> submit(Callable<T> task, String poolName) {
        LOG.debug("提交异步任务到 [{}]", poolName);
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            Future<T> poolFuture = threadPoolManager.submit(task, poolName);
            // 将普通 Future 包装为 CompletableFuture
            CompletableFuture.runAsync(() -> {
                try {
                    T result = poolFuture.get();
                    future.complete(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(e);
                } catch (ExecutionException e) {
                    future.completeExceptionally(e.getCause());
                } catch (CancellationException e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.warn("任务提交被拒绝 [{}]: {}", poolName, e.getMessage());
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * 提交一个带超时的异步任务。
     *
     * @param task     可调用的任务
     * @param poolName 线程池名称
     * @param timeout  超时时间
     * @param <T>      返回值类型
     * @return CompletableFuture（超时时会异常完成）
     */
    public <T> CompletableFuture<T> submit(Callable<T> task, String poolName, Duration timeout) {
        CompletableFuture<T> future = submit(task, poolName);
        // 用 orTimeout 实现超时（Java 21 内置方法）
        return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 异步执行一个无返回值的任务。
     *
     * @param task     可运行的任务
     * @param poolName 线程池名称
     * @return CompletableFuture
     */
    public CompletableFuture<Void> runAsync(Runnable task, String poolName) {
        return submit(() -> {
            task.run();
            return null;
        }, poolName);
    }
}
