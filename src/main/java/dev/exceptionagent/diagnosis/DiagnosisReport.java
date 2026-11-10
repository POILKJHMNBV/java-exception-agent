package dev.exceptionagent.diagnosis;

import java.util.List;

/**
 * 服务端校验并组装的公开诊断报告。
 * @param diagnosisId 服务端请求标识
 * @param status 流程完整性，complete 或 partial
 * @param exceptionType 最深主因类型，不可识别时为 null
 * @param evidence 可追溯的事实证据
 * @param possibleCauses 有证据的原因假设
 * @param suggestions 排查建议
 * @param uncertainties 诊断局限说明
 */
public record DiagnosisReport(String diagnosisId, String status, String exceptionType, List<Evidence> evidence,
                              List<Cause> possibleCauses, List<Suggestion> suggestions, List<String> uncertainties) {
    /** 固定所有集合，避免结果被后续处理改写。 */
    public DiagnosisReport {
        evidence = List.copyOf(evidence);
        possibleCauses = List.copyOf(possibleCauses);
        suggestions = List.copyOf(suggestions);
        uncertainties = List.copyOf(uncertainties);
    }

    /**
     * 服务端生成的证据，文本来自工具事实而非模型声明。
     * @param id 报告内唯一引用标识
     * @param source stackTrace 或 knowledgeBase
     * @param reference 输入行号字符串或知识条目 ID
     * @param text 原文或知识条目内容
     */
    public record Evidence(String id, String source, String reference, String text) { }

    /**
     * 尚待验证的原因假设。
     * @param description 假设描述
     * @param evidenceIds 至少一个本次有效证据标识
     */
    public record Cause(String description, List<String> evidenceIds) {
        /** 固定原因引用集合。 */
        public Cause { evidenceIds = List.copyOf(evidenceIds); }
    }

    /**
     * 可执行建议，通用排查步骤可以没有引用。
     * @param action 排查动作
     * @param evidenceIds 本次有效证据标识
     */
    public record Suggestion(String action, List<String> evidenceIds) {
        /** 固定建议引用集合。 */
        public Suggestion { evidenceIds = List.copyOf(evidenceIds); }
    }
}
