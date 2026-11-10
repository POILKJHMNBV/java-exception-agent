package dev.exceptionagent.diagnosis;

import dev.exceptionagent.tools.KnowledgeBase;
import dev.exceptionagent.tools.StackTraceParser;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.web.client.RestClient;

/** 装配生产编排与本地 Ollama 传输，启动时不访问或拉取模型。 */
@Configuration(proxyBeanMethods = false)
public class DiagnosisConfiguration {
    /** 关闭底层重试，确保一次 ChatClient 请求只有一次实际 HTTP 请求。 */
    @Bean
    RetryTemplate diagnosisRetryTemplate() {
        return new RetryTemplate(RetryPolicy.builder().maxRetries(0).predicate(exception -> false).build());
    }

    /** 提供本地模型协议客户端。 */
    @Bean
    OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        return OllamaApi.builder().baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(new CancellableHttpRequestFactory())).build();
    }

    /** 创建有界服务，由容器关闭线程资源。 */
    @Bean(destroyMethod = "close")
    DiagnosisService diagnosisService(ChatModel model, StackTraceParser parser, KnowledgeBase knowledge) {
        return new DiagnosisService(model, parser, knowledge);
    }
}
