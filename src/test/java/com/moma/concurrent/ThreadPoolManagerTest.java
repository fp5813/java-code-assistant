package com.moma.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线程池管理器单元测试。验证线程池提交、执行、统计和关闭功能。
 */
class ThreadPoolManagerTest {

    private ThreadPoolManager manager;

    @BeforeEach
    void setUp() {
        manager = new ThreadPoolManager();
        manager.init();
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    void testSubmitAndExecute() throws ExecutionException, InterruptedException {
        Callable<String> task = () -> "done";
        Future<String> future = manager.submit(task, "compute");
        assertEquals("done", future.get());
    }

    @Test
    void testRunnableSubmit() throws ExecutionException, InterruptedException {
        Future<?> future = manager.submit((Runnable) () -> {}, "compute");
        assertNull(future.get());
    }

    @Test
    void testInvalidPoolName() {
        assertThrows(IllegalArgumentException.class,
            () -> manager.submit(() -> "x", "invalidPool"));
    }

    @Test
    void testComputePool() throws Exception {
        Future<String> future = manager.submit(() -> "compute-result", "compute");
        assertEquals("compute-result", future.get());
    }

    @Test
    void testIoPool() throws Exception {
        Future<String> future = manager.submit(() -> "io-result", "io");
        assertEquals("io-result", future.get());
    }

    @Test
    void testCachePool() throws Exception {
        Future<String> future = manager.submit(() -> "cache-result", "cache");
        assertEquals("cache-result", future.get());
    }

    @Test
    void testEventPool() throws Exception {
        Future<String> future = manager.submit(() -> "event-result", "event");
        assertEquals("event-result", future.get());
    }

    @Test
    void testExecutor() throws Exception {
        java.util.concurrent.Executor executor = manager.getExecutor("compute");
        assertNotNull(executor);

        StringBuilder result = new StringBuilder();
        executor.execute(() -> result.append("executed"));
        Thread.sleep(100);
        assertEquals("executed", result.toString());
    }

    @Test
    void testGetStats() {
        var stats = manager.getStats();
        assertTrue(stats.containsKey("compute"));
        assertTrue(stats.containsKey("io"));
        assertTrue(stats.containsKey("cache"));
        assertTrue(stats.containsKey("event"));

        ThreadPoolManager.PoolStats computeStats = stats.get("compute");
        assertTrue(computeStats.poolSize() >= 0);
    }

    @Test
    void testShutdown() {
        // CallerRunsPolicy 在 shutdown 后不会抛出 RejectedExecutionException
        // 验证 shutdown 后提交的任务不被执行
        final boolean[] executed = {false};
        manager.shutdown();
        try {
            manager.submit(() -> { executed[0] = true; return null; }, "compute");
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 某些 JDK 实现可能抛出此异常（当 CallerRunsPolicy 被修改时）
        }
        // 验证 shutdown 后任务未被执行
        assertFalse(executed[0], "shutdown 后的任务不应被执行");
    }

    @Test
    void testMultipleTasks() throws Exception {
        int taskCount = 10;
        Future<?>[] futures = new Future<?>[taskCount];
        for (int i = 0; i < taskCount; i++) {
            final int id = i;
            futures[i] = manager.submit(() -> {
                Thread.sleep(50);
                return "task-" + id;
            }, "io");
        }

        for (int i = 0; i < taskCount; i++) {
            assertEquals("task-" + i, futures[i].get());
        }
    }

    @Test
    void testThreadNaming() throws Exception {
        Future<String> future = manager.submit(() -> {
            return Thread.currentThread().getName();
        }, "compute");

        String threadName = future.get();
        assertTrue(threadName.startsWith("compute-"),
            "线程名应以 'compute-' 开头，实际: " + threadName);
    }
}
