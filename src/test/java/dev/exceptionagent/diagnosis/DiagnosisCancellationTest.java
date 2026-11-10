package dev.exceptionagent.diagnosis;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import dev.exceptionagent.tools.KnowledgeBase;
import dev.exceptionagent.tools.StackTraceParser;
import org.junit.jupiter.api.Test;

import static dev.exceptionagent.diagnosis.DiagnosisServiceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用可控阻塞桩验证真实取消、并发容量及超时后的资源约束。 */
class DiagnosisCancellationTest {
    /** 两个交错执行的请求各自消费真实工具结果，不能串用类型、历史和预算。 */
    @Test
    void concurrentRequestsKeepSeparateToolContexts() throws Exception {
        CountDownLatch bothEntered = new CountDownLatch(2);
        Script model = new Script(prompt -> {
            List<String> results = toolResults(prompt);
            if (results.isEmpty()) {
                bothEntered.countDown();
                try { assertThat(bothEntered.await(2, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { throw new AssertionError(exception); }
                return calls(call(PARSE, "{}"));
            }
            if (results.size() == 1) {
                String type = results.get(0).contains("NullPointerException")
                        ? "java.lang.NullPointerException" : "java.lang.NumberFormatException";
                return calls(call(SEARCH, "{\"exceptionType\":\"" + type + "\"}"));
            }
            return answer(results.get(1));
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try (DiagnosisService service = service(model)) {
            Future<AgentResult> first = callers.submit(() -> service.diagnose(STACK));
            Future<AgentResult> second = callers.submit(() -> service.diagnose("java.lang.NumberFormatException: bad number"));
            AgentResult npe = first.get(5, TimeUnit.SECONDS);
            AgentResult number = second.get(5, TimeUnit.SECONDS);
            assertThat(npe.parsed().rootCause().exceptionType()).isEqualTo("java.lang.NullPointerException");
            assertThat(number.parsed().rootCause().exceptionType()).isEqualTo("java.lang.NumberFormatException");
            assertThat(npe.modelText()).contains("npe-001").doesNotContain("NumberFormatException");
            assertThat(number.modelText()).contains("NumberFormatException").doesNotContain("npe-001");
            assertThat(npe.diagnosisId()).isNotEqualTo(number.diagnosisId());
            assertThat(List.of(npe, number)).allSatisfy(result -> {
                assertThat(result.toolCalls()).isEqualTo(2);
                assertThat(result.modelCalls()).isEqualTo(3);
            });
        }
        finally { callers.shutdownNow(); }
    }

    /** 模型阻塞时须中断执行线程且不再调用工具。 */
    @Test
    void modelTimeoutInterruptsAndStopsLoop() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        Script model = new Script(prompt -> {
            interruptibleBlock(cancelled);
            return calls(call(PARSE, "{}"));
        });
        try (DiagnosisService service = limited(model, new StackTraceParser(), 2000, 150, 500)) {
            fails(service, "TIMEOUT");
            assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(model.prompts).hasSize(1);
        }
    }

    /** 本地工具超时须取消回调，不能向模型回传失败文本继续循环。 */
    @Test
    void toolTimeoutInterruptsAndStopsLoop() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        StackTraceParser parser = mock(StackTraceParser.class);
        when(parser.parse(STACK)).thenAnswer(invocation -> {
            interruptibleBlock(cancelled);
            return new StackTraceParser().parse(STACK);
        });
        Script model = new Script(List.of(calls(call(PARSE, "{}")), answer("unexpected")));
        try (DiagnosisService service = limited(model, parser, 2000, 1000, 100)) {
            fails(service, "TIMEOUT");
            assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(model.prompts).hasSize(1);
        }
    }

    /** 剩余总时限短于模型单步上限时，必须按剩余时间取消。 */
    @Test
    void totalDeadlineCapsStepWait() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        Script model = new Script(prompt -> {
            interruptibleBlock(cancelled);
            return answer("unexpected");
        });
        try (DiagnosisService service = limited(model, new StackTraceParser(), 120, 5000, 500)) {
            long start = System.nanoTime();
            fails(service, "TIMEOUT");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** 两个请求占满容量后立即拒绝第三个；忽略中断的残留任务继续占用槽位。 */
    @Test
    void retainsCapacityUntilUncooperativeTasksActuallyExit() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger interruptions = new AtomicInteger();
        Script model = new Script(prompt -> {
            entered.countDown();
            while (release.getCount() != 0) {
                try { release.await(); }
                catch (InterruptedException exception) { interruptions.incrementAndGet(); }
            }
            return answer("late");
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try (DiagnosisService service = limited(model, new StackTraceParser(), 5000, 1000, 500)) {
            Future<?> first = callers.submit(() -> fails(service, "TIMEOUT"));
            Future<?> second = callers.submit(() -> fails(service, "TIMEOUT"));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            fails(service, "CAPACITY_FULL");
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            fails(service, "CAPACITY_FULL");
            assertThat(model.prompts).hasSize(2);
            assertThat(interruptions.get()).isEqualTo(2);
        }
        finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    /** 调用方取消请求时须继续中断工作任务，保留调用方中断状态。 */
    @Test
    void callerInterruptionCancelsWorker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch callerDone = new CountDownLatch(1);
        Script model = new Script(prompt -> {
            entered.countDown();
            interruptibleBlock(cancelled);
            return answer("late");
        });
        try (DiagnosisService service = service(model)) {
            Thread caller = new Thread(() -> {
                try {
                    fails(service, "TIMEOUT");
                    assertThat(Thread.currentThread().isInterrupted()).isTrue();
                }
                finally { callerDone.countDown(); }
            });
            // 使用线程未捕获异常收集，确保辅助线程断言失败不会悄悄丢失。
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            caller.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
            caller.start();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            assertThat(callerDone.await(2, TimeUnit.SECONDS)).isTrue();
            caller.join(1000);
            assertThat(failure.get()).isNull();
            assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** 将时限缩短为毫秒以测试生产同一条调度路径。 */
    static DiagnosisService limited(Script model, StackTraceParser parser, long total, long modelTime, long toolTime) {
        AgentLimits limits = new AgentLimits(4, 5, Duration.ofMillis(total), Duration.ofMillis(modelTime), Duration.ofMillis(toolTime));
        return new DiagnosisService(model, parser, new KnowledgeBase(), limits, System::nanoTime);
    }

    /** 在中断到达前持续阻塞，用闩锁证明真实中断已送达。 */
    static void interruptibleBlock(CountDownLatch cancelled) {
        try { new CountDownLatch(1).await(); }
        catch (InterruptedException exception) {
            cancelled.countDown();
            Thread.currentThread().interrupt();
        }
    }
}
