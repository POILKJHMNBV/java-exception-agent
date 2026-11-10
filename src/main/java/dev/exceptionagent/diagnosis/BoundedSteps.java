package dev.exceptionagent.diagnosis;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** 两个请求槽位与两个工作线程；超时残留任务必须真正退出才能归还容量。 */
final class BoundedSteps implements AutoCloseable {
    /** 无阻塞获取的请求容量，不为外部请求建立等待队列。 */
    private final Semaphore slots = new Semaphore(2);
    /** 队列仅容纳两个已获准请求的步骤交接，永远不会无限堆积后台任务。 */
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), runnable -> {
                Thread thread = new Thread(runnable, "diagnosis-step");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    /** 尝试立即获得容量，关闭后或满载时拒绝。 */
    Lease acquire() {
        if (workers.isShutdown() || !slots.tryAcquire()) {
            throw new DiagnosisFailure("CAPACITY_FULL");
        }
        return new Lease();
    }

    /** 用剩余时间等待步骤，超时和调用方中断均真实取消工作任务。 */
    <T> T run(Lease lease, Callable<T> action, long nanos, boolean model) {
        if (nanos <= 0) { throw new DiagnosisFailure("TIMEOUT"); }
        Step<T> task = new Step<>(lease, action);
        lease.references.incrementAndGet();
        try { workers.execute(task); }
        catch (RejectedExecutionException exception) {
            lease.close();
            throw new DiagnosisFailure("CAPACITY_FULL");
        }
        try { return task.result.get(nanos, TimeUnit.NANOSECONDS); }
        catch (TimeoutException exception) {
            task.result.cancel(true);
            throw new DiagnosisFailure("TIMEOUT");
        }
        catch (InterruptedException exception) {
            task.result.cancel(true);
            Thread.currentThread().interrupt();
            throw new DiagnosisFailure("TIMEOUT");
        }
        catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DiagnosisFailure failure) { throw failure; }
            for (Throwable nested = cause; nested != null; nested = nested.getCause()) {
                if (nested instanceof HttpTimeoutException || nested instanceof SocketTimeoutException) {
                    throw new DiagnosisFailure("TIMEOUT");
                }
                if (model && nested instanceof ConnectException) {
                    throw new DiagnosisFailure("MODEL_UNAVAILABLE");
                }
            }
            throw new DiagnosisFailure(model ? "MODEL_FAILURE" : "TOOL_FAILURE");
        }
    }

    /** 取消活动步骤并清理尚未运行的任务引用，不无限等待不合作代码。 */
    @Override
    public void close() {
        List<Runnable> pending = workers.shutdownNow();
        for (Runnable runnable : pending) {
            Step<?> step = (Step<?>) runnable;
            step.result.cancel(true);
            step.run();
        }
    }

    /** 请求与当前工作任务共同持有的容量凭证。 */
    final class Lease implements AutoCloseable {
        /** 初始引用属于请求线程，步骤执行期间另持一个引用。 */
        private final AtomicInteger references = new AtomicInteger(1);

        /** 仅在请求和后台执行都结束时归还容量。 */
        @Override
        public void close() {
            if (references.decrementAndGet() == 0) { slots.release(); }
        }
    }

    /** 将 Future 的取消状态与工作线程实际退出分离，避免假释放容量。 */
    private static final class Step<T> implements Runnable {
        /** 接收结果并提供中断取消的 Future。 */
        private final FutureTask<T> result;
        /** 本任务持有的容量引用。 */
        private final Lease lease;

        /** 构建尚未提交的步骤。 */
        Step(Lease lease, Callable<T> action) {
            this.lease = lease;
            this.result = new FutureTask<>(action);
        }

        /** 即使被取消，仍等 run 真正退出才释放任务引用。 */
        @Override
        public void run() {
            try { result.run(); }
            finally { lease.close(); }
        }
    }
}
