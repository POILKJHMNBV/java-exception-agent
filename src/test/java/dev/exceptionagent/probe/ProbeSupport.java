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

/** 仅供接线测试使用的只读探针辅助代码，不注册为生产工具或 Bean。 */
final class ProbeSupport {
    /** 要求模型实际调用探针并原样返回结果的测试指令。 */
    static final String INSTRUCTION = "Call readProbeClock exactly once with label Day2. "
            + "After receiving the tool result, reply with only the exact stamp string. Do not invent a stamp.";

    /** 构建关闭自动工具循环的客户端，将执行授权留给测试宿主。 */
    static ChatClient client(ChatModel model) {
        return ChatClient.builder(model)
                .defaultAdvisors(AdvisorParams.toolCallingAdvisorAutoRegister(false)).build();
    }

    /** 构造仅注册时钟探针的测试请求。 */
    static Prompt prompt(ClockProbe probe) {
        return new Prompt(List.of(new UserMessage(INSTRUCTION)),
                ToolCallingChatOptions.builder().toolCallbacks(probe).build());
    }

    /** 真正读取时钟的测试回调，用执行次数和时间戳证明宿主调用发生。 */
    static final class ClockProbe implements ToolCallback {
        /** 可注入固定或系统时钟，分别服务确定性与真实模型测试。 */
        private final Clock clock;
        /** 此探针实例实际执行的次数。 */
        private int executions;
        /** 最近一次实际执行产生的时间戳。 */
        private String stamp;

        /** 绑定本次探针使用的时钟，保持实例状态独立。 */
        ClockProbe(Clock clock) {
            this.clock = clock;
        }

        /** 声明探针名称和严格参数结构，模型声明本身不代表实际执行。 */
        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name("readProbeClock")
                    .description("Read the actual local UTC clock; return its opaque stamp. Never invent it.")
                    .inputSchema("""
                            {"type":"object","properties":{"label":{"type":"string","enum":["Day2"]}},
                             "required":["label"],"additionalProperties":false}
                            """).build();
        }

        /** 校验唯一允许的参数后读取时钟，拒绝额外字段或错误标签。 */
        @Override
        public String call(String input) {
            JsonNode args = JsonMapper.builder().build().readTree(input);
            if (!args.isObject() || args.size() != 1 || !"Day2".equals(args.path("label").asString())) {
                throw new IllegalArgumentException("Invalid probe arguments");
            }
            // 只有参数检查通过后才记录执行并生成结果，不能用模型文本伪造时间戳。
            executions++;
            stamp = "probe:" + clock.instant();
            return stamp;
        }

        /** 返回实际执行次数，供闭环断言使用。 */
        int executions() { return executions; }
        /** 返回最近一次实际工具结果，供回传与最终输出比对。 */
        String stamp() { return stamp; }
    }
}
