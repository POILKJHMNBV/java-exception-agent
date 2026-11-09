package dev.exceptionagent.probe;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import static org.assertj.core.api.Assertions.assertThat;

class ChatClientWiringTest {
    @Test
    void callerControlsExecutionAndReturnsActualToolResultToModel() {
        ProbeSupport.ClockProbe probe = new ProbeSupport.ClockProbe(Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC));
        ScriptedModel model = new ScriptedModel();
        ChatClient client = ProbeSupport.client(model);
        Prompt prompt = ProbeSupport.prompt(probe);

        ChatResponse first = client.prompt(prompt).call().chatResponse();
        assertThat(model.prompts).as("automatic model loop is disabled").hasSize(1);
        assertThat(probe.executions()).as("host has not authorized execution yet").isZero();
        assertThat(first).isNotNull();
        assertThat(first.getResult().getOutput().getToolCalls()).containsExactly(
                new AssistantMessage.ToolCall("scripted-1", "function", "readProbeClock", "{\"label\":\"Day2\"}"));

        ToolExecutionResult executed = ToolCallingManager.builder().build().executeToolCalls(prompt, first);
        assertThat(probe.executions()).isEqualTo(1);
        assertThat(executed.returnDirect()).isFalse();
        List<Message> history = executed.conversationHistory();
        assertThat(history).hasSize(3);
        assertThat(history.get(1)).isEqualTo(first.getResult().getOutput());
        ToolResponseMessage resultMessage = (ToolResponseMessage) history.get(2);
        assertThat(resultMessage.getResponses()).containsExactly(
                new ToolResponseMessage.ToolResponse("scripted-1", "readProbeClock", probe.stamp()));

        ChatResponse last = client.prompt(new Prompt(history, prompt.getOptions())).call().chatResponse();
        assertThat(model.prompts).hasSize(2);
        assertThat(model.prompts.get(1).getInstructions()).containsExactlyElementsOf(history);
        assertThat(last).isNotNull();
        assertThat(last.hasToolCalls()).isFalse();
        assertThat(last.getResult().getOutput().getText()).isEqualTo(probe.stamp());
        assertThat(probe.executions()).isEqualTo(1);
    }

    /** Scripted model evidence only, never claimed as a real Ollama result. */
    static final class ScriptedModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();

        @Override
        public ToolCallingChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            assertThat(prompts.size()).isLessThanOrEqualTo(2);
            assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks())
                    .extracting(callback -> callback.getToolDefinition().name()).containsExactly("readProbeClock");
            if (prompts.size() == 1) {
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("scripted-1", "function",
                                "readProbeClock", "{\"label\":\"Day2\"}"))).build())));
            }
            List<Message> messages = prompt.getInstructions();
            ToolResponseMessage toolResult = (ToolResponseMessage) messages.get(messages.size() - 1);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    toolResult.getResponses().get(0).responseData()))));
        }
    }
}
