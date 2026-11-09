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
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in integration evidence, not a business-tool acceptance test. */
@Tag("ollama")
class OllamaProbeTest {
    @Test
    @Timeout(120)
    void realModelRequestsProbeAndUsesActualResult() throws Exception {
        var evidence = new ArrayList<Map<String, Object>>();
        var start = System.nanoTime();
        var outcome = "FAIL";
        try {
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build());
            factory.setReadTimeout(Duration.ofSeconds(55));
            var api = OllamaApi.builder().baseUrl("http://localhost:11434")
                    .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
            var manager = ToolCallingManager.builder().build();
            var ollama = OllamaChatModel.builder().ollamaApi(api)
                    .options(OllamaChatOptions.builder().model("qwen3.5:9b")
                            .temperature(0.0).disableThinking().maxTokens(256).build())
                    .modelManagementOptions(ModelManagementOptions.builder()
                            .pullModelStrategy(PullModelStrategy.NEVER).build())
                    .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
            var recorded = new RecordingModel(ollama, evidence);
            var probe = new ProbeSupport.ClockProbe(Clock.systemUTC());
            var client = ProbeSupport.client(recorded);
            var prompt = ProbeSupport.prompt(probe);
            evidence.add(Map.of("event", "configuration", "at", Instant.now().toString(),
                    "model", "qwen3.5:9b", "think", false, "temperature", 0,
                    "maxTokens", 256, "autoLoop", false, "maxRetries", 0,
                    "toolDefinition", probe.getToolDefinition()));

            var first = client.prompt(prompt).call().chatResponse();
            assertThat(recorded.calls).isEqualTo(1);
            assertThat(probe.executions()).isZero();
            assertThat(first).isNotNull();
            var calls = first.getResult().getOutput().getToolCalls();
            assertThat(calls).hasSize(1);
            assertThat(calls.get(0).name()).isEqualTo("readProbeClock");
            assertThat(calls.get(0).type()).isEqualTo("function");
            assertThat(calls.get(0).id()).isNotBlank();
            var args = JsonMapper.builder().build().readTree(calls.get(0).arguments());
            assertThat(args.isObject()).isTrue();
            assertThat(args.size()).isEqualTo(1);
            assertThat(args.path("label").asString()).isEqualTo("Day2");

            var executed = manager.executeToolCalls(prompt, first);
            assertThat(probe.executions()).isEqualTo(1);
            assertThat(executed.returnDirect()).isFalse();
            var history = executed.conversationHistory();
            var tool = (ToolResponseMessage) history.get(history.size() - 1);
            assertThat(tool.getResponses()).containsExactly(new ToolResponseMessage.ToolResponse(
                    calls.get(0).id(), "readProbeClock", probe.stamp()));
            evidence.add(Map.of("event", "host-execution", "executions", probe.executions(),
                    "stamp", probe.stamp(), "toolResponses", tool.getResponses()));

            var last = client.prompt(new Prompt(history, prompt.getOptions())).call().chatResponse();
            assertThat(last).isNotNull();
            assertThat(last.hasToolCalls()).isFalse();
            assertThat(last.getResult().getOutput().getText().trim()).isEqualTo(probe.stamp());
            assertThat(recorded.calls).isEqualTo(2);
            assertThat(probe.executions()).isEqualTo(1);
            outcome = "PASS";
        }
        finally {
            evidence.add(Map.of("event", "outcome", "status", outcome,
                    "elapsedMs", (System.nanoTime() - start) / 1_000_000));
            var path = Path.of("target", "ollama-probe-evidence.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(evidence), StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingModel implements ChatModel {
        private final ChatModel delegate;
        private final List<Map<String, Object>> evidence;
        private int calls;

        private RecordingModel(ChatModel delegate, List<Map<String, Object>> evidence) {
            this.delegate = delegate;
            this.evidence = evidence;
        }

        @Override
        public ChatOptions getOptions() { return delegate.getOptions(); }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls++;
            assertThat(calls).isLessThanOrEqualTo(2);
            var toolResults = prompt.getInstructions().stream()
                    .filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast).flatMap(m -> m.getResponses().stream()).toList();
            evidence.add(Map.of("event", "model-request", "number", calls,
                    "messageRoles", prompt.getInstructions().stream().map(m -> m.getMessageType().name()).toList(),
                    "toolResultsInContext", toolResults));
            var response = delegate.call(prompt);
            var output = response.getResult().getOutput();
            var entry = new LinkedHashMap<String, Object>();
            entry.put("event", "model-response");
            entry.put("number", calls);
            entry.put("toolCalls", output.getToolCalls());
            entry.put("text", output.getText());
            evidence.add(entry); // Deliberately exclude reasoning and response metadata.
            return response;
        }
    }
}
