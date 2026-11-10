package dev.exceptionagent.api;

import java.util.Set;
import dev.exceptionagent.diagnosis.DiagnosisFailure;
import dev.exceptionagent.diagnosis.DiagnosisReport;
import dev.exceptionagent.diagnosis.DiagnosisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 本地诊断 HTTP 入口，输入校验成功后才进入真实模型与工具编排。 */
@RestController
public final class DiagnosisController {
    /** 请求级有界编排服务，跨请求仅共享不可变配置和有界容量。 */
    private final DiagnosisService service;
    /** 不允许重复字段和尾随文档的严格 JSON 解码器。 */
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /** 注入已配置的生产编排服务。 */
    public DiagnosisController(DiagnosisService service) { this.service = service; }

    /** 严格校验类型和长度，不让宽松的字符串绑定隐式接受数字或布尔值。 */
    @PostMapping(value = "/api/diagnoses", consumes = "application/json", produces = "application/json")
    public DiagnosisReport diagnose(@RequestBody byte[] body, HttpServletRequest request) {
        JsonNode json;
        try { json = mapper.readTree(body); }
        catch (JacksonException exception) { throw new DiagnosisFailure("INVALID_REQUEST"); }
        if (json == null || !json.isObject() || !json.propertyNames().equals(Set.of("stackTrace"))
                || !json.path("stackTrace").isString() || json.path("stackTrace").asString().isBlank()) {
            throw new DiagnosisFailure("INVALID_REQUEST");
        }
        String stack = json.path("stackTrace").asString();
        // 按 Unicode 码点计数；CRLF 是一个换行，结尾空行也计入输入行数。
        if (stack.codePointCount(0, stack.length()) > 16000 || stack.split("\\r\\n|\\n|\\r", -1).length > 200) {
            throw new DiagnosisFailure("INPUT_TOO_LARGE");
        }
        return service.report(stack, (String) request.getAttribute(DiagnosisRequestFilter.ID_ATTRIBUTE));
    }
}
