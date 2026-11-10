package dev.exceptionagent.diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import dev.exceptionagent.tools.KnowledgeEntry;
import dev.exceptionagent.tools.ParsedStackTrace;

/** 一次诊断独占的状态，通过 ToolContext 传入回调，绝不作为共享 Bean。 */
final class RequestState {
    /** 服务端产生的关联标识。 */
    final String id;
    /** 服务端接收的原始输入，模型没有替换它的参数。 */
    final String stackTrace;
    /** 本次使用的硬限制。 */
    final AgentLimits limits;
    /** 单调时钟，允许确定性测试推进时间。 */
    private final LongSupplier clock;
    /** 开始时间，跨步骤不重置。 */
    private final long started;
    /** 成功调用的顺序，用于内部结果审计。 */
    final List<String> order = new ArrayList<>();
    /** 实际解析结果，尚未执行时为空。 */
    ParsedStackTrace parsed;
    /** 实际知识结果；null 区分尚未调用与合法空命中。 */
    List<KnowledgeEntry> knowledge;
    /** 已发起的模型请求次数。 */
    int modelCalls;
    /** 已授权调度的工具次数。 */
    int toolCalls;

    /** 绑定不可替换输入与请求开始时间。 */
    RequestState(String stackTrace, AgentLimits limits, LongSupplier clock) {
        this(stackTrace, limits, clock, UUID.randomUUID().toString());
    }

    /** 沿用 HTTP 入口产生的关联标识，使错误和成功都能定位同一请求。 */
    RequestState(String stackTrace, AgentLimits limits, LongSupplier clock, String id) {
        this.id = id;
        this.stackTrace = stackTrace;
        this.limits = limits;
        this.clock = clock;
        this.started = clock.getAsLong();
    }

    /** 使用差值避免 nanoTime 起点及加法溢出影响总预算。 */
    long remaining() {
        long remaining = limits.total().toNanos() - (clock.getAsLong() - started);
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
            throw new DiagnosisFailure("TIMEOUT");
        }
        return remaining;
    }
}
