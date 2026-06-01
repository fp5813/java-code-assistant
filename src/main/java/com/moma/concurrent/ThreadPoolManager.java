package com.moma.concurrent;

import com.moma.di.Component;
import com.moma.di.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池管理器。管理 4 个专用线程池。
 * 单例，通过 DI 容器管理生命周期。
 */
@Component
public class ThreadPoolManager {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolManager.class);

    private ThreadPoolExecutor computePool;
    private ThreadPoolExecutor ioPool;
    private ThreadPoolExecutor cachePool;
    private ThreadPoolExecutor eventPool;

    private final Map<String, ThreadPoolExecutor> pools = new ConcurrentHashMap<>();

    /** 自定义线程工厂：为每个线程池的线程命名 */
    private static ThreadFactory namedThreadFactory(String poolName) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, poolName + "-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
    }

    @PostConstruct
    public void init() {
        int cpus = Runtime.getRuntime().availableProcessors();
        LOG.info("初始化线程池管理器 (CPU核心数: {})", cpus);

        // ── 计算密集型线程池：核心=CPU数, 最大=CPU数, SynchronousQueue（无容量） ──
        computePool = new ThreadPoolExecutor(
            cpus, cpus, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            namedThreadFactory("compute"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        computePool.allowCoreThreadTimeOut(false);

        // ── I/O 密集型线程池：核心=2*CPU, 最大=4*CPU, 有界队列 1000 ──
        ioPool = new ThreadPoolExecutor(
            cpus * 2, cpus * 4, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            namedThreadFactory("io"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        ioPool.allowCoreThreadTimeOut(true);

        // ── 缓存线程池：核心=4, 最大=8, 有界队列 100 ──
        cachePool = new ThreadPoolExecutor(
            4, 8, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            namedThreadFactory("cache"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        cachePool.allowCoreThreadTimeOut(true);

        // ── 事件线程池：核心=2, 最大=4, 有界队列 200 ──
        eventPool = new ThreadPoolExecutor(
            2, 4, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            namedThreadFactory("event"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        eventPool.allowCoreThreadTimeOut(true);

        // 注册到 Map
        pools.put("compute", computePool);
        pools.put("io", ioPool);
        pools.put("cache", cachePool);
        pools.put("event", eventPool);

        // 添加 JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "threadpool-shutdown-hook"));

        LOG.info("线程池管理器初始化完成: compute={}/{}, io={}/{}, cache={}/{}, event={}/{}",
            computePool.getCorePoolSize(), computePool.getMaximumPoolSize(),
            ioPool.getCorePoolSize(), ioPool.getMaximumPoolSize(),
            cachePool.getCorePoolSize(), cachePool.getMaximumPoolSize(),
            eventPool.getCorePoolSize(), eventPool.getMaximumPoolSize());
    }

    /**
     * 提交任务到指定线程池。
     *
     * @param task     任务
     * @param poolName 线程池名称 (compute/io/cache/event)
     * @param <T>      返回值类型
     * @return Future
     * @throws IllegalArgumentException 如果池名不存在
     */
    public <T> Future<T> submit(Callable<T> task, String poolName) {
        ThreadPoolExecutor pool = pools.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("未知线程池: " + poolName
                + " (可用: compute, io, cache, event)");
        }
        LOG.debug("提交任务到线程池 [{}]: 活跃={}, 队列深度={}",
            poolName, pool.getActiveCount(), pool.getQueue().size());
        return pool.submit(task);
    }

    /**
     * 提交 Runnable 任务到指定线程池。
     */
    public Future<?> submit(Runnable task, String poolName) {
        ThreadPoolExecutor pool = pools.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("未知线程池: " + poolName);
        }
        return pool.submit(task);
    }

    /**
     * 获取指定线程池的 Executor 引用。
     */
    public Executor getExecutor(String poolName) {
        ThreadPoolExecutor pool = pools.get(poolName);
        if (pool == null) {
            throw new IllegalArgumentException("未知线程池: " + poolName);
        }
        return pool;
    }

    /**
     * 优雅关闭所有线程池。
     */
    public void shutdown() {
        LOG.info("开始关闭所有线程池...");
        for (Map.Entry<String, ThreadPoolExecutor> entry : pools.entrySet()) {
            String name = entry.getKey();
            ThreadPoolExecutor pool = entry.getValue();
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOG.warn("线程池 [{}] 未在 10s 内终止，强制关闭", name);
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOG.warn("线程池 [{}] 关闭被中断", name, e);
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("所有线程池已关闭");
    }

    /**
     * 获取所有线程池的统计信息。
     *
     * @return 池名 -> 统计数据
     */
    public Map<String, PoolStats> getStats() {
        Map<String, PoolStats> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, ThreadPoolExecutor> entry : pools.entrySet()) {
            ThreadPoolExecutor pool = entry.getValue();
            stats.put(entry.getKey(), new PoolStats(
                pool.getPoolSize(),
                pool.getActiveCount(),
                pool.getQueue().size(),
                pool.getCompletedTaskCount(),
                pool.getTaskCount()
            ));
        }
        return stats;
    }

    /**
     * 线程池统计数据。
     */
    public record PoolStats(
        int poolSize,
        int activeCount,
        int queueDepth,
        long completedTasks,
        long totalTasks
    ) {}
}
