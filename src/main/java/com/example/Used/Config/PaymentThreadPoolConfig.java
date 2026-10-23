package com.example.Used.Config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The payment thread pool (Step 3 of the payment process).
 *
 * Every payment runs as a TASK on this shared, fixed-size pool — never on the
 * HTTP request thread, and never on a throwaway thread-per-request.
 *
 * Why fixed + bounded queue:
 *   - The number of SIMULTANEOUS payments (= simultaneous DB row locks) is
 *     capped at `core-size`. Lock contention stays bounded and predictable.
 *   - The pool must stay <= HikariCP's connection pool size (default 10),
 *     otherwise threads would just queue up waiting for connections.
 *   - When the queue is full the request is REJECTED (AbortPolicy) and the
 *     caller gets a clean 429/503 — the marketplace says "busy, retry"
 *     instead of collapsing under a burst of buyers.
 */
@Configuration
public class PaymentThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentThreadPoolConfig.class);

    @Value("${payment.pool.core-size:4}")
    private int coreSize;

    @Value("${payment.pool.queue-capacity:50}")
    private int queueCapacity;

    @Value("${payment.pool.shutdown-wait-seconds:30}")
    private int shutdownWaitSeconds;

    @Bean(name = "paymentExecutor")
    public ThreadPoolExecutor paymentExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                coreSize,                        // fixed size: core == max
                0L, TimeUnit.MILLISECONDS,       // no idle shrink: pool is fixed
                new LinkedBlockingQueue<>(queueCapacity),  // bounded burst buffer
                new PaymentThreadFactory(),      // payment-worker-1 .. N
                new ThreadPoolExecutor.AbortPolicy()       // queue full -> RejectedExecutionException
        );
        executor.allowCoreThreadTimeOut(false);
        log.info("[PAYMENT-POOL] started: {} workers, queue capacity {}",
                coreSize, queueCapacity);
        return executor;
    }

    /**
     * Graceful shutdown: stop accepting new payments, give in-flight ones
     * (each is one DB transaction) up to 30s to commit, then force.
     *
     * NOTE: this works because a @Bean method always returns the same singleton
     * within the configuration class. Other classes must INJECT the executor
     * bean instead of calling this method.
     */
    @PreDestroy
    public void shutdownPaymentExecutor() {
        ThreadPoolExecutor executor = paymentExecutor();
        log.info("[PAYMENT-POOL] shutting down, waiting up to {}s for {} in-flight payment(s)",
                shutdownWaitSeconds, executor.getActiveCount());
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownWaitSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("[PAYMENT-POOL] forced shutdown, some payments may have been interrupted");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Names threads payment-worker-1..N so logs and thread dumps are readable. */
    static class PaymentThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "payment-worker-" + counter.getAndIncrement());
            t.setDaemon(false); // stay alive until in-flight payments finish
            return t;
        }
    }
}
