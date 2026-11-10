package dev.exceptionagent.diagnosis;

import java.time.Duration;

/**
 * 编排硬限制；包内测试可缩短时间，生产仅使用固定默认值。
 * @param tools 每请求工具次数
 * @param models 每请求模型次数
 * @param total 请求总时限
 * @param model 单次模型时限
 * @param tool 单次工具时限
 */
record AgentLimits(int tools, int models, Duration total, Duration model, Duration tool) {
    /** 架构规定的生产预算。 */
    static AgentLimits defaults() {
        return new AgentLimits(4, 5, Duration.ofSeconds(120), Duration.ofSeconds(60), Duration.ofSeconds(1));
    }
}
