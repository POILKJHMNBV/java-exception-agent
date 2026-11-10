package dev.exceptionagent.diagnosis;

/** 编排失败的稳定分类，供后续 HTTP 层映射；消息不携带输入或底层异常。 */
public final class DiagnosisFailure extends RuntimeException {
    /** 本次失败的机器可读代码。 */
    private final String code;

    /** 只保存固定代码，避免底层异常中的敏感内容进入响应。 */
    public DiagnosisFailure(String code) {
        super(code);
        this.code = code;
    }

    /** 返回稳定错误代码。 */
    public String code() { return code; }
}
