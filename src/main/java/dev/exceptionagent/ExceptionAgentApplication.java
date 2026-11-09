package dev.exceptionagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 本地 Java 异常诊断应用的 Spring Boot 启动入口。 */
@SpringBootApplication
public class ExceptionAgentApplication {
    /** 启动应用并由 Spring 装配固定配置与业务组件。 */
    public static void main(String[] args) {
        SpringApplication.run(ExceptionAgentApplication.class, args);
    }
}
