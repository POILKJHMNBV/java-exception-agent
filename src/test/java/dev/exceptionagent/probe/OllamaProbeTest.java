package dev.exceptionagent.probe;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 必须显式启用的真实 Ollama 接线探针，不代替业务工具验收。 */
@Tag("ollama")
class OllamaProbeTest {
    /** 保存结构化请求、宿主执行、结果回传与最终输出组成的真实闭环证据。 */
    @Test
    @Timeout(120)
    void realModelRequestsProbeAndUsesActualResult() throws Exception {
        List<Map<String, Object>> evidence = new ArrayList<Map<String, Object>>();
        long start = System.nanoTime();
        String outcome = "FAIL";
        try {
            // 真实探针使用有限连接/读取超时，禁止自动拉取模型及自动重试。
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build());
            factory.setReadTimeout(Duration.ofSeconds(55));
            OllamaApi api = OllamaApi.builder().baseUrl("http://localhost:11434")
                    .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
            ToolCallingManager manager = ToolCallingManager.builder().build();
            OllamaChatModel ollama = OllamaChatModel.builder().ollamaApi(api)
                    .options(OllamaChatOptions.builder().model("qwen3.5:9b")
                            .temperature(0.0).disableThinking().maxTokens(256).build())
                    .modelManagementOptions(ModelManagementOptions.builder()
                            .pullModelStrategy(PullModelStrategy.NEVER).build())
                    .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
            RecordingModel recorded = new RecordingModel(ollama, evidence);
            ProbeSupport.ClockProbe probe = new ProbeSupport.ClockProbe(Clock.systemUTC());
            ChatClient client = ProbeSupport.client(recorded);
            Prompt prompt = ProbeSupport.prompt(probe);
            evidence.add(Map.of("event", "configuration", "at", Instant.now().toString(),
                    "model", "qwen3.5:9b", "think", false, "temperature", 0,
                    "maxTokens", 256, "autoLoop", false, "maxRetries", 0,
                    "toolDefinition", probe.getToolDefinition()));

            ChatResponse first = client.prompt(prompt).call().chatResponse();
            assertThat(recorded.calls).isEqualTo(1);
            assertThat(probe.executions()).isZero();
            assertThat(first).isNotNull();
            List<AssistantMessage.ToolCall> calls = first.getResult().getOutput().getToolCalls();
            assertThat(calls).hasSize(1);
            assertThat(calls.get(0).name()).isEqualTo("readProbeClock");
            assertThat(calls.get(0).type()).isEqualTo("function");
            assertThat(calls.get(0).id()).isNotBlank();
            JsonNode args = JsonMapper.builder().build().readTree(calls.get(0).arguments());
            assertThat(args.isObject()).isTrue();
            assertThat(args.size()).isEqualTo(1);
            assertThat(args.path("label").asString()).isEqualTo("Day2");

            // 先核验模型发出的实际结构化参数，再允许宿主执行回调。
            ToolExecutionResult executed = manager.executeToolCalls(prompt, first);
            assertThat(probe.executions()).isEqualTo(1);
            assertThat(executed.returnDirect()).isFalse();
            List<Message> history = executed.conversationHistory();
            ToolResponseMessage tool = (ToolResponseMessage) history.get(history.size() - 1);
            assertThat(tool.getResponses()).containsExactly(new ToolResponseMessage.ToolResponse(
                    calls.get(0).id(), "readProbeClock", probe.stamp()));
            evidence.add(Map.of("event", "host-execution", "executions", probe.executions(),
                    "stamp", probe.stamp(), "toolResponses", tool.getResponses()));

            ChatResponse last = client.prompt(new Prompt(history, prompt.getOptions())).call().chatResponse();
            assertThat(last).isNotNull();
            assertThat(last.hasToolCalls()).isFalse();
            assertThat(last.getResult().getOutput().getText().trim()).isEqualTo(probe.stamp());
            assertThat(recorded.calls).isEqualTo(2);
            assertThat(probe.executions()).isEqualTo(1);
            outcome = "PASS";
        }
        finally {
            // 成败都保存本次证据，避免只保留成功样本；历史归档由交付流程另行保存。
            evidence.add(Map.of("event", "outcome", "status", outcome,
                    "elapsedMs", (System.nanoTime() - start) / 1_000_000));
            Path path = Path.of("target", "ollama-probe-evidence.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(evidence), StandardCharsets.UTF_8);
        }
    }

    /** 委托真实模型调用并记录必要闭环证据，不记录思考内容。 */
    private static final class RecordingModel implements ChatModel {
        /** 实际负责请求 Ollama 的模型实例。 */
        private final ChatModel delegate;
        /** 本次探针共享的事件记录列表。 */
        private final List<Map<String, Object>> evidence;
        /** 已向真实模型发起的请求次数。 */
        private int calls;

        /** 绑定实际模型与本次证据收集器。 */
        private RecordingModel(ChatModel delegate, List<Map<String, Object>> evidence) {
            this.delegate = delegate;
            this.evidence = evidence;
        }

        /** 透传真实模型选项，避免记录包装器改变调用配置。 */
        @Override
        public ChatOptions getOptions() { return delegate.getOptions(); }

        /** 记录请求角色、工具结果和模型输出，同时限制探针最多两轮请求。 */
        @Override
        public ChatResponse call(Prompt prompt) {
            calls++;
            assertThat(calls).isLessThanOrEqualTo(2);
            List<ToolResponseMessage.ToolResponse> toolResults = prompt.getInstructions().stream()
                    .filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast).flatMap(m -> m.getResponses().stream()).toList();
            evidence.add(Map.of("event", "model-request", "number", calls,
                    "messageRoles", prompt.getInstructions().stream().map(m -> m.getMessageType().name()).toList(),
                    "toolResultsInContext", toolResults));
            ChatResponse response = delegate.call(prompt);
            AssistantMessage output = response.getResult().getOutput();
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("event", "model-response");
            entry.put("number", calls);
            entry.put("toolCalls", output.getToolCalls());
            entry.put("text", output.getText());
            evidence.add(entry); // 只记录闭环所需字段，排除思考内容及响应元数据。
            return response;
        }
    }
}
