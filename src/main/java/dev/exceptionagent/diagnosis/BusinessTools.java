package dev.exceptionagent.diagnosis;

import java.util.List;
import dev.exceptionagent.tools.KnowledgeBase;
import dev.exceptionagent.tools.StackTraceParser;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 固定的两个只读回调，输入和状态只从宿主提供的 ToolContext 获取。 */
final class BusinessTools {
    /** 请求私有上下文键，不是模型参数。 */
    static final String CONTEXT_KEY = "diagnosisRequest";
    /** 解析工具名称。 */
    static final String PARSE = "parseStackTrace";
    /** 查询工具名称。 */
    static final String SEARCH = "searchKnowledgeBase";
    /** 拒绝重复字段和尾随文档的参数解析器。 */
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    /** Day 3 的无状态解析器。 */
    private final StackTraceParser parser;
    /** Day 3 的不可变只读知识库。 */
    private final KnowledgeBase knowledge;

    /** 绑定实际业务组件，不在构造时提前解析输入。 */
    BusinessTools(StackTraceParser parser, KnowledgeBase knowledge) {
        this.parser = parser;
        this.knowledge = knowledge;
    }

    /** 仅返回白名单内两个回调；不允许名称解析扩展。 */
    List<ToolCallback> callbacks() { return List.of(new Callback(PARSE), new Callback(SEARCH)); }

    /** 执行前严格检查参数、名称及当前阶段，不信任模型的 schema 遵守声明。 */
    static JsonNode validate(String name, String arguments, RequestState state) {
        try {
            JsonNode args = JSON.readTree(arguments);
            if (args == null || !args.isObject()) { throw illegal(); }
            if (PARSE.equals(name)) {
                if (!args.isEmpty() || state.knowledge != null) { throw illegal(); }
            }
            else if (SEARCH.equals(name)) {
                if (state.parsed == null || args.size() != 1 || !args.path("exceptionType").isString()) { throw illegal(); }
                String expected = state.parsed.rootCause() == null ? "" : state.parsed.rootCause().exceptionType();
                if (!expected.equals(args.path("exceptionType").asString())) { throw illegal(); }
            }
            else { throw illegal(); }
            return args;
        }
        catch (RuntimeException exception) { throw illegal(); }
    }

    /** 构造不会泄露原始参数的固定错误。 */
    private static DiagnosisFailure illegal() { return new DiagnosisFailure("ILLEGAL_TOOL_CALL"); }

    /** 两种工具共享宿主上下文校验，但只执行各自固定业务。 */
    private final class Callback implements ToolCallback {
        /** 此回调唯一允许的工具名称。 */
        private final String name;

        /** 固定名称，不接受模型动态指定业务。 */
        Callback(String name) { this.name = name; }

        /** 提供无任意路径、URL 或原文替换入口的严格 schema。 */
        @Override
        public ToolDefinition getToolDefinition() {
            String schema = PARSE.equals(name) ? """
                    {"type":"object","properties":{},"additionalProperties":false}
                    """ : """
                    {"type":"object","properties":{"exceptionType":{"type":"string"}},
                     "required":["exceptionType"],"additionalProperties":false}
                    """;
            return ToolDefinition.builder().name(name).description(PARSE.equals(name)
                    ? "Parse the original request stack trace. Call this first with no arguments."
                    : "Search local knowledge for the exact parsed root exception type; use empty string if unknown.")
                    .inputSchema(schema).build();
        }

        /** 禁止脱离请求上下文直接调用。 */
        @Override
        public String call(String input) { throw illegal(); }

        /** 在实际执行处再次确认上下文、剩余时间与参数，然后发布本次结果。 */
        @Override
        public String call(String input, ToolContext context) {
            if (context == null || !(context.getContext().get(CONTEXT_KEY) instanceof RequestState state)) { throw illegal(); }
            state.remaining();
            JsonNode args = validate(name, input, state);
            String result;
            if (PARSE.equals(name)) {
                state.parsed = parser.parse(state.stackTrace);
                if (state.parsed == null) { throw new DiagnosisFailure("TOOL_FAILURE"); }
                result = JSON.writeValueAsString(state.parsed);
            }
            else {
                state.knowledge = List.copyOf(knowledge.search(args.path("exceptionType").asString(), state.parsed));
                result = JSON.writeValueAsString(state.knowledge);
            }
            state.remaining();
            state.order.add(name);
            return result;
        }
    }
}
