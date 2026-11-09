package dev.exceptionagent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.ai.tool.ToolCallback;
import dev.exceptionagent.tools.KnowledgeBase;
import dev.exceptionagent.tools.StackTraceParser;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证固定配置、业务组件加载以及不依赖 Ollama 可达性的应用启动行为。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.ai.ollama.base-url=http://127.0.0.1:1"
})
class ApplicationSmokeTest {
    /** 用于检查自动配置、知识库与解析器装配结果的应用上下文。 */
    @Autowired
    ApplicationContext context;

    /** 在不可达模型地址下启动应用，并核对模型配置和实际知识资源。 */
    @Test
    void startsWithoutOllamaAndBindsFixedModelSettings() {
        OllamaChatOptions options = (OllamaChatOptions) context.getBean(OllamaChatModel.class).getOptions();
        assertThat(context.getBean(ChatClient.Builder.class)).isNotNull();
        assertThat(options.getModel()).isEqualTo("qwen3.5:9b");
        assertThat(options.getTemperature()).isEqualTo(0.0);
        assertThat(options.getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
        assertThat(options.getMaxTokens()).isEqualTo(2048);
        assertThat(context.getBeansOfType(ToolCallback.class)).isEmpty();
        StackTraceParser parser = context.getBean(StackTraceParser.class);
        KnowledgeBase knowledge = context.getBean(KnowledgeBase.class);
        assertThat(knowledge.search("java.lang.NullPointerException", parser.parse(
                "java.lang.NullPointerException\n\tat example.Main.main(Main.java:1)")))
                .extracting(entry -> entry.id()).containsExactly("npe-001");
        assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
    }
}
