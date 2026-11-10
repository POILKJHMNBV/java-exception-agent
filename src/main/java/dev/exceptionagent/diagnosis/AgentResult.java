package dev.exceptionagent.diagnosis;

import java.util.List;
import dev.exceptionagent.tools.KnowledgeEntry;
import dev.exceptionagent.tools.ParsedStackTrace;

/**
 * 内部编排事实与未校验模型文本；HTTP 入口必须经 ReportValidator 后才能返回业务报告。
 * @param diagnosisId 服务端关联标识
 * @param modelText 未校验的模型输出
 * @param parsed 实际工具解析结果
 * @param knowledge 本次实际知识命中
 * @param modelCalls 实际发起的模型请求次数
 * @param toolCalls 实际调度的工具次数
 * @param toolOrder 成功执行的工具顺序
 */
public record AgentResult(String diagnosisId, String modelText, ParsedStackTrace parsed,
                          List<KnowledgeEntry> knowledge, int modelCalls, int toolCalls, List<String> toolOrder) {
    /** 固定集合快照，避免后续请求修改已完成结果。 */
    public AgentResult {
        knowledge = List.copyOf(knowledge);
        toolOrder = List.copyOf(toolOrder);
    }
}
