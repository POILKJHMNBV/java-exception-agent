package dev.exceptionagent.api;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实内嵌 HTTP 容器核验过滤器自动注册及 chunked 正文限制，无需 Ollama。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.ollama.base-url=http://127.0.0.1:1")
@Import(DiagnosisApiTest.ModelConfiguration.class)
class DiagnosisHttpTransportTest {
    /** 容器分配的本地临时端口，避免占用用户的 8080。 */
    @Value("${local.server.port}")
    private int port;

    /** 实际发送未知长度正文，精确上限可用，超过一个字节就拒绝。 */
    @Test
    void enforcesChunkedBodyThroughRealContainer() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        String json = "{\"stackTrace\":\"unknown input\"}";
        String exact = json + " ".repeat(128 * 1024 - json.length());
        HttpResponse<String> success = send(client, exact);
        assertThat(success.statusCode()).isEqualTo(200);
        JsonNode report = JsonMapper.builder().build().readTree(success.body());
        assertThat(report.path("status").asString()).isEqualTo("partial");
        assertThat(report.path("exceptionType").isNull()).isTrue();
        assertThat(report.path("diagnosisId").asString()).isEqualTo(success.headers().firstValue("X-Diagnosis-Id").orElseThrow());
        HttpResponse<String> oversized = send(client, exact + " ");
        assertThat(oversized.statusCode()).isEqualTo(413);
        JsonNode failure = JsonMapper.builder().build().readTree(oversized.body());
        assertThat(failure.path("code").asString()).isEqualTo("INPUT_TOO_LARGE");
        assertThat(failure.path("diagnosisId").asString()).isEqualTo(oversized.headers().firstValue("X-Diagnosis-Id").orElseThrow());
        assertThat(failure.path("diagnosisId").asString()).isNotEqualTo(report.path("diagnosisId").asString());
    }

    /** 输入流发布器不声明 Content-Length，HTTP/1.1 实际使用 chunked 传输。 */
    private HttpResponse<String> send(HttpClient client, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/diagnoses"))
                .version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
