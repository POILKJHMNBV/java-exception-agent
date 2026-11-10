package dev.exceptionagent.diagnosis;

import java.util.List;
import java.util.stream.Stream;
import dev.exceptionagent.tools.KnowledgeBase;
import dev.exceptionagent.tools.ParsedStackTrace;
import dev.exceptionagent.tools.StackTraceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T7.1 报告校验测试：模型不具备制造事实、类型或引用的权限。 */
class ReportValidatorTest {
    /** 含实际来源行及知识命中的合法样例。 */
    private static final String STACK = "java.lang.NullPointerException: secret\n\tat app.Main.run(Main.java:7)";
    /** 模型只输出解释字段，证据标识遵循固定来源规则。 */
    private static final String VALID = """
            {"possibleCauses":[{"description":"对象可能为空","evidenceIds":["stack-1","kb-npe-001"]}],
             "suggestions":[{"action":"检查参数","evidenceIds":[]}],"uncertainties":["缺少源码"]}
            """;
    /** 无原因依据时仍可给出通用建议和不确定性。 */
    private static final String EMPTY = """
            {"possibleCauses":[],"suggestions":[{"action":"提供源码","evidenceIds":[]}],"uncertainties":["缺少源码"]}
            """;

    /** 完整报告只包含实际原文及知识，类型与 ID 由服务端固定。 */
    @Test
    void assemblesVerifiedCompleteReport() {
        DiagnosisReport report = new ReportValidator().validate(result(STACK, VALID));
        assertThat(report.diagnosisId()).isEqualTo("request-1");
        assertThat(report.status()).isEqualTo("complete");
        assertThat(report.exceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(report.evidence()).extracting(DiagnosisReport.Evidence::id)
                .containsExactly("stack-1", "stack-2", "kb-npe-001");
        assertThat(report.evidence().get(0)).isEqualTo(new DiagnosisReport.Evidence(
                "stack-1", "stackTrace", "1", "java.lang.NullPointerException: secret"));
        assertThat(report.evidence().get(1).text()).isEqualTo("\tat app.Main.run(Main.java:7)");
        assertThat(report.evidence().get(2).reference()).isEqualTo("npe-001");
        assertThat(report.evidence().get(2).text()).contains(new KnowledgeBase().search(
                "java.lang.NullPointerException", new StackTraceParser().parse(STACK)).get(0).causes().get(0));
        assertThat(report.possibleCauses().get(0).description()).startsWith("可能原因（待验证）：");
        assertThat(report.suggestions().get(0).evidenceIds()).isEmpty();
        assertThat(report.uncertainties()).contains("缺少源码");
        assertThatThrownBy(() -> report.evidence().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    /** 主因选择和行引用使用真实解析结果，包括父异常与最深主因。 */
    @Test
    void preservesRootCauseAndAllRecognizedFrames() {
        String stack = "java.lang.IllegalArgumentException\n\tat app.Main.run(Main.java:1)\n"
                + "Caused by: java.lang.NullPointerException\n\tat app.Main.run(Main.java:2)";
        DiagnosisReport report = new ReportValidator().validate(result(stack, VALID));
        assertThat(report.exceptionType()).isEqualTo("java.lang.NullPointerException");
        assertThat(report.evidence()).extracting(DiagnosisReport.Evidence::id)
                .containsExactly("stack-1", "stack-2", "stack-3", "stack-4", "kb-npe-001");
    }

    /** 未识别、未命中、无法解释、省略与 Suppressed 均不能标记完整。 */
    @ParameterizedTest
    @MethodSource("partialStacks")
    void marksPartialFromFacts(String stack) {
        DiagnosisReport report = new ReportValidator().validate(result(stack, EMPTY));
        assertThat(report.status()).isEqualTo("partial");
        assertThat(report.possibleCauses()).isEmpty();
        assertThat(report.uncertainties().size()).isGreaterThan(1);
        if (stack.equals("unrecognized input")) {
            assertThat(report.exceptionType()).isNull();
            assertThat(report.evidence()).isEmpty();
        }
    }

    /** 独立列出不同局限来源，防止仅凭模型的 complete 声明通过。 */
    static Stream<String> partialStacks() {
        return Stream.of("unrecognized input", "app.UnknownException\n\tat app.Main.run(Main.java:1)",
                "java.lang.NullPointerException", STACK + "\ninvalid line", STACK + "\n\t... 2 more",
                STACK + "\n\tSuppressed: java.lang.IllegalArgumentException");
    }

    /** 非法 JSON、形状、引用和模型制造事实一律拒绝，不降级为成功。 */
    @ParameterizedTest
    @MethodSource("invalidReports")
    void rejectsInvalidReport(String text) {
        assertThatThrownBy(() -> new ReportValidator().validate(result(STACK, text)))
                .isInstanceOf(DiagnosisFailure.class).hasMessage("INVALID_REPORT").hasNoCause();
    }

    /** 包含重复字段、尾随文档和空引用等不能由宽松反序列化发现的攻击。 */
    static Stream<String> invalidReports() {
        return Stream.of(null, "", "null", "[]", "{", "```json\n" + VALID + "```", VALID + " {}",
                VALID.replace("\"uncertainties\":[\"缺少源码\"]", "\"uncertainties\":[]"),
                VALID.replace("\"缺少源码\"", "null"), VALID.replace("\"缺少源码\"", "42"),
                VALID.replace("\"缺少源码\"", "\"  \""), VALID.replace("\"suggestions\":", "\"suggestions\":[],\"suggestions\":"),
                VALID.replace("[\"stack-1\",\"kb-npe-001\"]", "[]"), VALID.replace("stack-1", "stack-999"),
                VALID.replace("kb-npe-001", "kb-iae-001"), VALID.replace("\"检查参数\"", "false"),
                VALID.replace("\"evidenceIds\":[]", "\"evidenceIds\":[\"fake\"]"),
                VALID.replace("\"possibleCauses\":", "\"exceptionType\":\"fake\",\"possibleCauses\":"),
                "{}", VALID.replace("\"evidenceIds\":[]", "\"evidenceIds\":null"),
                VALID.replace("\"description\":", "\"text\":\"fake fact\",\"description\":"));
    }

    /** 前一个请求命中的知识及输入行不能为后一个请求提供证据。 */
    @Test
    void isolatesEvidenceCatalogs() {
        ReportValidator validator = new ReportValidator();
        validator.validate(result(STACK, VALID));
        assertThatThrownBy(() -> validator.validate(result("unrecognized input", VALID)))
                .isInstanceOf(DiagnosisFailure.class).hasMessage("INVALID_REPORT");
    }

    /** 使用实际解析器与知识库构造校验输入，避免伪造来源。 */
    private static AgentResult result(String stack, String text) {
        ParsedStackTrace parsed = new StackTraceParser().parse(stack);
        String type = parsed.rootCause() == null ? "" : parsed.rootCause().exceptionType();
        return new AgentResult("request-1", text, parsed, new KnowledgeBase().search(type, parsed),
                3, 2, List.of("parseStackTrace", "searchKnowledgeBase"));
    }
}
