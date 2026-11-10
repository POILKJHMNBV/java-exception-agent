package dev.exceptionagent.api;

/**
 * 对外错误仅含稳定分类与固定文字，绝不包含模型文本或内部异常。
 * @param diagnosisId 服务端关联标识
 * @param code 稳定错误码
 * @param message 安全的错误说明
 */
public record ApiError(String diagnosisId, String code, String message) {
    /** 对未知错误码统一脱敏，避免异常消息被误当作公开代码。 */
    static ApiError of(String id, String code) {
        String message = switch (code) {
            case "INVALID_REQUEST" -> "请求必须包含非空字符串 stackTrace，且为合法 JSON。";
            case "INPUT_TOO_LARGE" -> "请求超过字节、码点或行数限制。";
            case "UNSUPPORTED_MEDIA_TYPE" -> "仅支持 application/json。";
            case "MODEL_UNAVAILABLE" -> "本地模型服务不可达。";
            case "CAPACITY_FULL" -> "诊断容量已满，请稍后重试。";
            case "KNOWLEDGE_UNAVAILABLE" -> "本地知识库不可用。";
            case "TIMEOUT" -> "诊断超过允许时限。";
            case "TOOL_FAILURE", "MODEL_FAILURE", "TOOL_BUDGET_EXHAUSTED", "MODEL_BUDGET_EXHAUSTED",
                    "ILLEGAL_TOOL_CALL", "MISSING_TOOLS", "INVALID_REPORT" -> "诊断未完成或报告无法校验。";
            default -> "服务暂时无法处理此请求。";
        };
        return new ApiError(id, status(code) == 500 ? "INTERNAL_ERROR" : code, message);
    }

    /** 将业务失败与 HTTP 契约集中映射，过滤器与 MVC 使用同一分类。 */
    static int status(String code) {
        return switch (code) {
            case "INVALID_REQUEST" -> 400;
            case "INPUT_TOO_LARGE" -> 413;
            case "UNSUPPORTED_MEDIA_TYPE" -> 415;
            case "MODEL_UNAVAILABLE", "CAPACITY_FULL", "KNOWLEDGE_UNAVAILABLE" -> 503;
            case "TIMEOUT" -> 504;
            case "TOOL_FAILURE", "MODEL_FAILURE", "TOOL_BUDGET_EXHAUSTED", "MODEL_BUDGET_EXHAUSTED",
                    "ILLEGAL_TOOL_CALL", "MISSING_TOOLS", "INVALID_REPORT" -> 502;
            default -> 500;
        };
    }
}
