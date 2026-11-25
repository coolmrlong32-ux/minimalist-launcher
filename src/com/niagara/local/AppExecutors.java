package com.niagara.local;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 全局应用线程池单例：
 * 避免随意 new Thread().start() 造成孤儿线程堆积与内核调度抖动。
 * - diskIO: 单线程顺序执行磁盘/文件读写与缓存操作，避免文件锁并发冲突
 * - computation: 轻量固定大小线程池，执行应用加载、壁纸渲染、日历事件查询等耗时运算
 * - mainThread: 主线程分发，便于在后台执行完后直接切回 UI 线程
 */
public final class AppExecutors {

    private static volatile AppExecutors sInstance;

    private final ExecutorService diskIO;
    private final ExecutorService computation;
    private final Executor mainThread;

    private AppExecutors() {
        this.diskIO = Executors.newSingleThreadExecutor(new NamedThreadFactory("Niagara-DiskIO"));
        // 根据 CPU 核心数分配适度计算线程（最少 2 个，最多 4 个），保持高效且省电
        int cores = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        this.computation = Executors.newFixedThreadPool(cores, new NamedThreadFactory("Niagara-Compute"));
        this.mainThread = new MainThreadExecutor();
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (AppExecutors.class) {
                if (sInstance == null) {
                    sInstance = new AppExecutors();
                }
            }
        }
        return sInstance;
    }

    public static void execute(Runnable r) {
        getInstance().computation().execute(r);
    }

    public ExecutorService diskIO() {
        return diskIO;
    }

    public ExecutorService computation() {
        return computation;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            mainThreadHandler.post(command);
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private int count = 1;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + (count++));
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    }
}
