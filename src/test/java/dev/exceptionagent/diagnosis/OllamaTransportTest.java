package dev.exceptionagent.diagnosis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import dev.exceptionagent.tools.KnowledgeBase;
import dev.exceptionagent.tools.StackTraceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static dev.exceptionagent.diagnosis.DiagnosisServiceTest.fails;
import static org.assertj.core.api.Assertions.assertThat;

/** 本地 HTTP 故障服务验证真实 Ollama 协议传输；不访问 Ollama 或模型文件。 */
class OllamaTransportTest {
    /** 完整协议路径验证 UTF-8、模型参数、白名单、实际工具结果回传及最终内容消费。 */
    @Test
    void fullHttpProtocolReturnsActualBusinessResults() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 2);
        server.createContext("/api/chat", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(request);
            String message;
            if (requests.size() == 1) {
                message = """
                        {"role":"assistant","content":"","tool_calls":[{"id":"p1",
                         "function":{"name":"parseStackTrace","arguments":{}}}]}
                        """;
            }
            else if (requests.size() == 2) {
                message = """
                        {"role":"assistant","content":"","tool_calls":[{"id":"s1",
                         "function":{"name":"searchKnowledgeBase","arguments":{"exceptionType":"java.lang.NullPointerException"}}}]}
                        """;
            }
            else {
                JsonNode messages = request.path("messages");
                String actual = messages.get(messages.size() - 1).path("content").asString();
                message = json.writeValueAsString(java.util.Map.of("role", "assistant", "content", actual));
            }
            byte[] response = ("{\"model\":\"qwen3.5:9b\",\"done\":true,\"message\":" + message + "}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try (DiagnosisService service = DiagnosisServiceTest.service(model("http://localhost:" + server.getAddress().getPort()))) {
            AgentResult result = service.diagnose("java.lang.NullPointerException: 中文业务消息\n\tat sample.Main.run(Main.java:7)");
            assertThat(result.parsed().rootCause().message()).isEqualTo("中文业务消息");
            assertThat(result.toolOrder()).containsExactly("parseStackTrace", "searchKnowledgeBase");
            assertThat(result.modelText()).isEqualTo(json.writeValueAsString(result.knowledge()));
            assertThat(requests).hasSize(3).allSatisfy(request -> {
                assertThat(request.path("model").asString()).isEqualTo("qwen3.5:9b");
                assertThat(request.path("stream").asBoolean()).isFalse();
                assertThat(request.path("think").asBoolean()).isFalse();
                assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(2048);
                assertThat(request.path("options").path("temperature").asDouble()).isZero();
                assertThat(request.path("tools").size()).isEqualTo(2);
                assertThat(request.toString()).doesNotContain("diagnosisRequest");
            });
            assertThat(requests.get(1).path("messages").toString()).contains("中文业务消息", "parseStackTrace", "tool");
            assertThat(requests.get(2).path("messages").toString()).contains("npe-001", "searchKnowledgeBase");
            // 只归档测试夹具的闭环摘要；不将生产原始堆栈或思考内容写入日志。
            List<JsonNode> structuredCalls = new java.util.ArrayList<>();
            List<String> returnedResults = new java.util.ArrayList<>();
            for (JsonNode message : requests.get(2).path("messages")) {
                if (message.has("tool_calls")) { structuredCalls.add(message.path("tool_calls")); }
                if ("tool".equals(message.path("role").asString())) { returnedResults.add(message.path("content").asString()); }
            }
            JsonNode returnedParse = json.readTree(returnedResults.get(0));
            java.util.Map<String, Object> evidence = java.util.Map.of(
                    "source", "deterministic local HTTP fixture; not real Ollama inference",
                    "structuredCalls", structuredCalls,
                    "hostToolOrder", result.toolOrder(),
                    "modelCalls", result.modelCalls(),
                    "toolCalls", result.toolCalls(),
                    "returnedParseType", returnedParse.path("exceptionChain").get(0).path("exceptionType").asString(),
                    "returnedKnowledge", json.readTree(returnedResults.get(1)),
                    "hostKnowledgeIds", result.knowledge().stream().map(entry -> entry.id()).toList(),
                    "finalModelText", result.modelText(),
                    "finalMatchesActualKnowledge", result.modelText().equals(json.writeValueAsString(result.knowledge())));
            java.nio.file.Files.writeString(java.nio.file.Path.of("target", "day4-protocol-loop.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence), StandardCharsets.UTF_8);
        }
        finally { server.stop(0); }
    }

    /** 默认重试策略必须拒绝所有重试，防止隐藏请求绕过次数限制。 */
    @Test
    void productionRetryPolicyNeverRetries() {
        assertThat(new DiagnosisConfiguration().diagnosisRetryTemplate().getRetryPolicy()
                .shouldRetry(new IOException("transport failed"))).isFalse();
    }

    /** 在收到响应头前和正文中途两种阻塞下，取消都必须关闭实际 TCP 请求。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutClosesActualSocket(boolean partialBody) throws Exception {
        ExecutorService serverThread = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            CountDownLatch received = new CountDownLatch(1);
            Future<Boolean> closed = serverThread.submit(() -> observeCancellation(server, received, partialBody));
            OllamaChatModel model = model("http://localhost:" + server.getLocalPort());
            AgentLimits limits = new AgentLimits(4, 5, Duration.ofSeconds(5), Duration.ofMillis(900), Duration.ofSeconds(1));
            try (DiagnosisService service = new DiagnosisService(model, new StackTraceParser(), new KnowledgeBase(), limits, System::nanoTime)) {
                fails(service, "TIMEOUT");
                assertThat(received.await(1, TimeUnit.SECONDS)).as("真实 HTTP 请求到达故障服务").isTrue();
                assertThat(closed.get(3, TimeUnit.SECONDS)).as("超时取消后对端观察到 TCP EOF").isTrue();
            }
        }
        finally { serverThread.shutdownNow(); }
    }

    /** 不可达端口映射为模型不可用，不能暴露底层连接异常。 */
    @Test
    void unreachableModelIsUnavailable() throws Exception {
        int port;
        try (ServerSocket reserved = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = reserved.getLocalPort();
        }
        try (DiagnosisService service = DiagnosisServiceTest.service(model("http://localhost:" + port))) {
            fails(service, "MODEL_UNAVAILABLE");
        }
    }

    /** 真实 500 响应只发起一次 HTTP 请求，不能由默认 Spring AI 重试放大。 */
    @Test
    void serverErrorIsNotRetried() throws Exception {
        DiagnosisConfiguration configuration = new DiagnosisConfiguration();
        // 先断言策略，以免尚未实现的 Red 运行默认长时间退避。
        assertThat(configuration.diagnosisRetryTemplate().getRetryPolicy().shouldRetry(new IOException())).isFalse();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 2);
        server.createContext("/api/chat", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try (DiagnosisService service = DiagnosisServiceTest.service(model("http://localhost:" + server.getAddress().getPort()))) {
            fails(service, "MODEL_FAILURE");
            assertThat(requests.get()).isEqualTo(1);
        }
        finally { server.stop(0); }
    }

    /** 按生产同一配置构建真实 OllamaChatModel，唯一替换项是本地故障端点。 */
    static OllamaChatModel model(String baseUrl) {
        DiagnosisConfiguration configuration = new DiagnosisConfiguration();
        return OllamaChatModel.builder().ollamaApi(configuration.ollamaApi(baseUrl))
                .options(OllamaChatOptions.builder().model("qwen3.5:9b").temperature(0.0).disableThinking().numPredict(2048).build())
                .retryTemplate(configuration.diagnosisRetryTemplate()).build();
    }

    /** 读取真实请求后保持响应不完整，直到对端取消关闭连接。 */
    static boolean observeCancellation(ServerSocket server, CountDownLatch received, boolean partialBody) throws Exception {
        try (Socket socket = server.accept()) {
            socket.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            int length = 0;
            boolean chunked = false;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                    length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
                if (line.toLowerCase(java.util.Locale.ROOT).contains("transfer-encoding: chunked")) { chunked = true; }
            }
            if (chunked) {
                int chunk;
                while ((chunk = Integer.parseInt(reader.readLine().trim(), 16)) != 0) {
                    for (int i = 0; i < chunk; i++) { reader.read(); }
                    reader.readLine();
                }
                reader.readLine();
            }
            else { for (int i = 0; i < length; i++) { reader.read(); } }
            received.countDown();
            if (partialBody) {
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: 10000\r\n\r\n{\"model\":").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            }
            return reader.read() == -1;
        }
    }
}
