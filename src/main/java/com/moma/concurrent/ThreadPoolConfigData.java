package com.moma.concurrent;

/**
 * 线程池配置数据模型。
 */
public record ThreadPoolConfigData(
    String name,
    int coreSize,
    int maxSize,
    int queueCapacity,
    long keepAliveSeconds
) {

    /** 计算密集型线程池 */
    public static ThreadPoolConfigData compute() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolConfigData("compute-pool", cpus, cpus, -1, 60);
    }

    /** I/O 密集型线程池 */
    public static ThreadPoolConfigData io() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolConfigData("io-pool", cpus * 2, cpus * 4, 1000, 60);
    }

    /** 缓存操作线程池 */
    public static ThreadPoolConfigData cache() {
        return new ThreadPoolConfigData("cache-pool", 4, 8, 100, 30);
    }

    /** 事件分发线程池 */
    public static ThreadPoolConfigData event() {
        return new ThreadPoolConfigData("event-pool", 2, 4, 200, 30);
    }
}
