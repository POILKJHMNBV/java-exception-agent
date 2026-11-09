package dev.exceptionagent.probe;

import java.time.Clock;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Test-only read-only probe; never a production tool or bean. */
final class ProbeSupport {
    static final String INSTRUCTION = "Call readProbeClock exactly once with label Day2. "
            + "After receiving the tool result, reply with only the exact stamp string. Do not invent a stamp.";

    static ChatClient client(ChatModel model) {
        return ChatClient.builder(model)
                .defaultAdvisors(AdvisorParams.toolCallingAdvisorAutoRegister(false)).build();
    }

    static Prompt prompt(ClockProbe probe) {
        return new Prompt(List.of(new UserMessage(INSTRUCTION)),
                ToolCallingChatOptions.builder().toolCallbacks(probe).build());
    }

    static final class ClockProbe implements ToolCallback {
        private final Clock clock;
        private int executions;
        private String stamp;

        ClockProbe(Clock clock) {
            this.clock = clock;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name("readProbeClock")
                    .description("Read the actual local UTC clock; return its opaque stamp. Never invent it.")
                    .inputSchema("""
                            {"type":"object","properties":{"label":{"type":"string","enum":["Day2"]}},
                             "required":["label"],"additionalProperties":false}
                            """).build();
        }

        @Override
        public String call(String input) {
            JsonNode args = JsonMapper.builder().build().readTree(input);
            if (!args.isObject() || args.size() != 1 || !"Day2".equals(args.path("label").asString())) {
                throw new IllegalArgumentException("Invalid probe arguments");
            }
            executions++;
            stamp = "probe:" + clock.instant();
            return stamp;
        }

        int executions() { return executions; }
        String stamp() { return stamp; }
    }
}
