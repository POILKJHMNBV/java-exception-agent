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

/** 使用脚本模型验证真实 ChatClient、工具执行器与回调之间的确定性接线。 */
class ChatClientWiringTest {
    /** 验证宿主授权前不执行工具，执行结果进入后续模型上下文并被最终输出使用。 */
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

        // 保留真实执行器与回调，只替换模型响应，避免整段业务 mock 掩盖接线问题。
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

    /** 只产生脚本化测试响应，不将其作为真实 Ollama 调用证据。 */
    static final class ScriptedModel implements ChatModel {
        /** 捕获实际收到的请求，以核对调用次数和回传上下文。 */
        final List<Prompt> prompts = new ArrayList<>();

        /** 提供工具调用选项，使测试走真实工具调用接线。 */
        @Override
        public ToolCallingChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        /** 首轮返回结构化调用，第二轮原样使用实际工具回传结果。 */
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
