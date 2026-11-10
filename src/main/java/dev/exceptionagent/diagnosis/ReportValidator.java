package dev.exceptionagent.diagnosis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.exceptionagent.tools.KnowledgeEntry;
import dev.exceptionagent.tools.ParsedStackTrace.ExceptionNode;
import dev.exceptionagent.tools.ParsedStackTrace.ParseWarning;
import dev.exceptionagent.tools.ParsedStackTrace.StackFrame;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 校验模型解释与本次工具事实，拒绝不可信报告。 */
public final class ReportValidator {
    /** 严格解析，不接受重复字段及尾随文档，也不做字符串类型强制转换。 */
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /** 根据本次工具结果组装报告，所有可变集合只存在于当前调用。 */
    public DiagnosisReport validate(AgentResult result) {
        if (result.modelText() == null || result.modelText().isBlank()) { throw invalid(); }
        JsonNode root;
        try { root = mapper.readTree(result.modelText()); }
        catch (JacksonException exception) { throw invalid(); }
        fields(root, Set.of("possibleCauses", "suggestions", "uncertainties"));
        Map<String, DiagnosisReport.Evidence> catalog = catalog(result);
        List<DiagnosisReport.Cause> causes = new ArrayList<>();
        for (JsonNode cause : array(root.get("possibleCauses"))) {
            fields(cause, Set.of("description", "evidenceIds"));
            List<String> ids = references(cause.get("evidenceIds"), catalog);
            if (ids.isEmpty()) { throw invalid(); }
            causes.add(new DiagnosisReport.Cause("可能原因（待验证）：" + text(cause.get("description")), ids));
        }
        List<DiagnosisReport.Suggestion> suggestions = new ArrayList<>();
        for (JsonNode suggestion : array(root.get("suggestions"))) {
            fields(suggestion, Set.of("action", "evidenceIds"));
            suggestions.add(new DiagnosisReport.Suggestion(text(suggestion.get("action")),
                    references(suggestion.get("evidenceIds"), catalog)));
        }
        List<String> uncertainties = texts(root.get("uncertainties"));
        if (uncertainties.isEmpty()) { throw invalid(); }
        // 模型的文字声明不能掩盖工具已经发现的局限，也不能把 complete 当作根因确定。
        uncertainties.add("缺少源码与运行时数据，原因假设仍需验证。");
        ExceptionNode cause = result.parsed().rootCause();
        if (cause == null) { uncertainties.add("无法识别异常主因类型。"); }
        if (result.knowledge().isEmpty()) { uncertainties.add("本地知识库未命中。"); }
        for (ParseWarning warning : result.parsed().warnings()) {
            uncertainties.add("输入第 " + warning.lineNumber() + " 行存在解析局限：" + warning.code());
        }
        boolean partial = cause == null || result.knowledge().isEmpty() || !result.parsed().warnings().isEmpty();
        return new DiagnosisReport(result.diagnosisId(), partial ? "partial" : "complete",
                cause == null ? null : cause.exceptionType(), List.copyOf(catalog.values()), causes,
                suggestions, uncertainties.stream().distinct().toList());
    }

    /** 保留所有已识别主链行，知识文本仅从本次命中序列化，绝不复制模型事实字段。 */
    private Map<String, DiagnosisReport.Evidence> catalog(AgentResult result) {
        Map<String, DiagnosisReport.Evidence> catalog = new LinkedHashMap<>();
        for (ExceptionNode node : result.parsed().exceptionChain()) {
            stackEvidence(catalog, node.lineNumber(), node.text());
            for (StackFrame frame : node.frames()) { stackEvidence(catalog, frame.lineNumber(), frame.text()); }
        }
        for (KnowledgeEntry entry : result.knowledge()) {
            String id = "kb-" + entry.id();
            catalog.put(id, new DiagnosisReport.Evidence(id, "knowledgeBase", entry.id(), mapper.writeValueAsString(entry)));
        }
        return catalog;
    }

    /** 输入证据标识直接编码实际输入行号，便于模型引用和用户核对。 */
    private static void stackEvidence(Map<String, DiagnosisReport.Evidence> catalog, int line, String text) {
        String id = "stack-" + line;
        catalog.put(id, new DiagnosisReport.Evidence(id, "stackTrace", Integer.toString(line), text));
    }

    /** 拒绝额外字段及缺失字段，特别是模型尝试指定事实、类型或完成状态。 */
    private static void fields(JsonNode node, Set<String> names) {
        if (node == null || !node.isObject() || !node.propertyNames().equals(names)) { throw invalid(); }
    }

    /** 所有集合均须显式出现并为数组，null 不等同于空列表。 */
    private static JsonNode array(JsonNode node) {
        if (node == null || !node.isArray()) { throw invalid(); }
        return node;
    }

    /** 文本必须非空且类型准确，不将布尔值或数字转换成字符串。 */
    private static String text(JsonNode node) {
        if (node == null || !node.isString() || node.asString().isBlank()) { throw invalid(); }
        return node.asString();
    }

    /** 建立当前请求独有的可追加文本集合。 */
    private static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array(node)) { values.add(text(item)); }
        return values;
    }

    /** 所有引用必须属于本次目录，不能跨请求借用或猜测知识条目。 */
    private static List<String> references(JsonNode node, Map<String, DiagnosisReport.Evidence> catalog) {
        List<String> ids = texts(node);
        if (ids.stream().anyMatch(id -> !catalog.containsKey(id))) { throw invalid(); }
        return ids;
    }

    /** 不把模型原文或解析错误上下文暴露给调用方。 */
    private static DiagnosisFailure invalid() { return new DiagnosisFailure("INVALID_REPORT"); }
}
