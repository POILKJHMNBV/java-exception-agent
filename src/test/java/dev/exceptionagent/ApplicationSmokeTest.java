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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.ai.ollama.base-url=http://127.0.0.1:1"
})
class ApplicationSmokeTest {
    @Autowired
    ApplicationContext context;

    @Test
    void startsWithoutOllamaAndBindsFixedModelSettings() {
        OllamaChatOptions options = (OllamaChatOptions) context.getBean(OllamaChatModel.class).getOptions();
        assertThat(context.getBean(ChatClient.Builder.class)).isNotNull();
        assertThat(options.getModel()).isEqualTo("qwen3.5:9b");
        assertThat(options.getTemperature()).isEqualTo(0.0);
        assertThat(options.getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
        assertThat(options.getMaxTokens()).isEqualTo(2048);
        assertThat(context.getBeansOfType(ToolCallback.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
    }
}
