# Day 4 / T6 交付记录

日期：2026-11-10（Asia/Shanghai）。阶段状态以 [plan.md](plan.md) 为唯一记录。

用户于 2026-11-10 明确确认 Day 4 验收通过，并授权提交、推送和运行本次远程 CI。T6 的实现、Review 与本地验证已完成；本次同时按要求精简证据并更新 AGENTS.md。Day 5 未获启动授权。

## 范围与验收

依赖 T3 接线、T4 解析、T5 知识库，执行 T6/A6 的有界编排。保留已验收产品、架构和所有固定版本，不新增依赖或工具。

| 验收项 | 完成结果与证据 |
| --- | --- |
| 实际两工具闭环 | 脚本 ChatModel 驱动真实 ChatClient、ToolCallingManager、解析器和知识库；另有真实 OllamaChatModel 连接本地 HTTP 夹具的完整协议测试，验证 UTF-8、固定模型参数、结构化调用、实际工具结果回传、最终输出消费 |
| 白名单、参数和阶段 | 仅 parseStackTrace / searchKnowledgeBase；拒绝未知名称、非 function、非法 JSON/重复键/尾随文档/额外字段/类型错误、未解析先查询、查询错误主因和查询后重新解析；原文从请求 ToolContext 获取 |
| 次数预算 | 每 function 计数，批量和重复调用均不能绕过 4 次工具上限；独立限制 5 次模型请求，允许第 5 轮在工具完成后给出最终输出；关闭底层重试，HTTP 500 仅发起一次请求 |
| 缺失工具与失败 | 自然语言声称已调用、只完成一个工具、工具执行异常、模型连接失败均可控退出；固定内部错误码不携带底层异常或敏感内容，HTTP 映射留至 Day 5 |
| 时间与取消 | 单调总时限 120 秒、模型 60 秒、连接 3 秒、工具 1 秒；每步等待取剩余总时限与单步上限的较小值；验证真实中断、无后续调度以及响应头前/部分正文两类 TCP 关闭 |
| 容量与隔离 | 最多两个诊断请求，满载立即拒绝；有界工作线程与内部交接队列；忽略中断的任务退出前仍占用槽位，避免超时反复放入新任务；验证双请求交错执行与独立输入、历史、工具结果、计数和关联 ID |

内部 `AgentResult.modelText` 是未校验文本，尚非业务报告；本阶段不声称 A1/A2/A5 或真实模型业务验收通过。

## 变更文件

| 文件 | 职责 |
| --- | --- |
| [DiagnosisService.java](../src/main/java/dev/exceptionagent/diagnosis/DiagnosisService.java) | 关闭自动工具循环，逐轮请求模型，逐个授权原始结构化调用；保留原始批量 assistant 消息并回传真实结果 |
| [BusinessTools.java](../src/main/java/dev/exceptionagent/diagnosis/BusinessTools.java) | 两工具定义、严格 JSON 参数校验、请求上下文读取与真实业务执行 |
| [RequestState.java](../src/main/java/dev/exceptionagent/diagnosis/RequestState.java)、[AgentLimits.java](../src/main/java/dev/exceptionagent/diagnosis/AgentLimits.java) | 请求私有事实、计数、单调时钟与固定生产预算 |
| [BoundedSteps.java](../src/main/java/dev/exceptionagent/diagnosis/BoundedSteps.java) | 有界执行、取消、容量凭证与错误归一化 |
| [CancellableHttpRequestFactory.java](../src/main/java/dev/exceptionagent/diagnosis/CancellableHttpRequestFactory.java) | JDK 17 可中断的完整响应传输，3 秒连接和 60 秒请求上限；不跟随重定向 |
| [DiagnosisConfiguration.java](../src/main/java/dev/exceptionagent/diagnosis/DiagnosisConfiguration.java) | 注册生产服务、传输和零重试配置；容器关闭时释放编排资源 |
| [AgentResult.java](../src/main/java/dev/exceptionagent/diagnosis/AgentResult.java)、[DiagnosisFailure.java](../src/main/java/dev/exceptionagent/diagnosis/DiagnosisFailure.java) | 内部不可变结果快照与固定失败代码，供 Day 5 使用 |
| [DiagnosisServiceTest.java](../src/test/java/dev/exceptionagent/diagnosis/DiagnosisServiceTest.java) | 28 项编排、工具、参数、预算与隔离测试 |
| [DiagnosisCancellationTest.java](../src/test/java/dev/exceptionagent/diagnosis/DiagnosisCancellationTest.java) | 6 项取消、总时限、容量与并发隔离测试 |
| [OllamaTransportTest.java](../src/test/java/dev/exceptionagent/diagnosis/OllamaTransportTest.java) | 6 项本地 HTTP 协议、取消、连接失败与无重试测试；生成闭环摘要 |
| [ApplicationSmokeTest.java](../src/test/java/dev/exceptionagent/ApplicationSmokeTest.java) | 补充生产服务与无重试 Bean 装配断言；仍在不可达 Ollama 地址下启动 |
| [plan.md](plan.md)、本记录、[day4 证据目录](evidence/day4/) | 阶段门禁、任务事实、Red/Green/Review 和协议闭环归档 |

## TDD 与最终验证

以下保留原始执行事实的摘要，失败未改写为成功；完整过程日志已按用户于 2026-11-10 的要求删除。模型响应来自明确的脚本/HTTP 夹具，不是手工伪装的真实模型调用证据。

| 步骤与命令 | 实际结果 |
| --- | --- |
| Red：`mvn -B -ntp '-Dtest=DiagnosisServiceTest' test` | 28 项，28 个业务断言失败，0 错误；骨架尚不执行编排 |
| Red：`mvn -B -ntp '-Dtest=DiagnosisCancellationTest' test` | 5 项，5 个断言失败，0 错误；尚无取消/容量实现 |
| Green：`mvn -B -ntp '-Dtest=DiagnosisServiceTest,DiagnosisCancellationTest' test` | 33/33 通过 |
| Red：`mvn -B -ntp '-Dtest=OllamaTransportTest' test` | 5 项，2 个无重试断言失败、1 个部分正文取消后仍未 EOF 的 SocketTimeoutException、2 项通过 |
| 第一次传输修复复验：同上 | 仍为 2 失败、1 错误；JDK Spring 请求工厂仍未解决半正文关闭问题，单独 maxRetries=0 也不令异常谓词返回 false |
| Green：同上 | 完整正文使用可中断 JDK send；重试次数与异常谓词均禁用，5/5 通过 |
| 初次全量：`mvn -B -ntp verify` | 109/109 通过，JAR 打包成功 |
| Review 回归：`mvn -B -ntp '-Dtest=OllamaTransportTest,DiagnosisCancellationTest,ApplicationSmokeTest' test` | 新增完整 HTTP 闭环和双请求交错断言，选中集合 13/13 通过 |
| 最终：`mvn -B -ntp verify` | **111/111，通过；0 失败、0 错误、0 跳过；JAR 打包成功**。2026-11-10 12:54:16 |

固定环境：JDK 17.0.11、Maven 3.8.4、Spring Boot 4.1.1、Spring AI 2.0.1。默认排除 ollama 标签；本次未运行 `-Pollama-it`，也未启动、拉取或调用本地真实模型。

[协议闭环摘要](evidence/day4/t6-protocol-loop.json) 由最终测试实际捕获生成：3 次模型 HTTP 请求、2 次真实工具执行；保存原始结构化调用、执行顺序、回传主因类型、实际知识结果、最终文本及其相等断言。只包含合成测试夹具数据，没有真实用户堆栈或模型思考内容。

## Review、偏差与风险

- 范围 Review 通过：单模块、单 Agent、仅两个本地只读业务工具；未修改 product.md、architecture.md、POM、知识条目或已验收的解析行为。
- 预算与资源 Review 通过：不是只对 Future 等待设超时；取消会中断执行并关闭实际模型请求。有界线程池最多 2 个工作线程、容量为 2 的内部步骤交接队列。外部请求没有等待队列；请求凭证与后台任务共同持有容量，残留任务不退出就不恢复对应容量。
- 数据与隔离 Review 通过：堆栈和知识只作数据；回调没有路径/URL/命令入口；历史与 ToolContext 每请求独立。默认不记录堆栈、完整提示词或模型思考；返回文本仍须 Day 5 校验，不能据此声称推理正确。
- 显式类型与中文注释 Review 通过：`rg '\bvar\b' src -g '*.java'` 零命中；具名类型、字段、构造器/方法和 record 组件自动检查无缺失，中文内容与关键边界注释人工复核。运行 `jshell '-J-Dfile.encoding=UTF-8' '-R-Dfile.encoding=UTF-8' scripts/review-java.jsh`，可复用[检查脚本](../scripts/review-java.jsh)。扫描 23 个文件、0 缺失；匿名类型不具有独立具名声明，已人工复核。早期扫描将已有匿名类误报为缺失，另一次 PowerShell 未引用 `-D` 参数失败；失败事实保留于本段摘要，不计通过。JShell 注册表偏好写入警告不影响最终 AST 扫描。
- `git diff --check` 通过。Review 为当前单 Agent 自查，没有委派或伪称独立评审。
- 实测促成的是既定“取消并关闭客户端请求”决策的实现细化：改用完整正文处于可中断 JDK send 内的非流式传输适配器。没有更换框架版本或放宽时间/次数预算。
- HTTP 取消不能保证 Ollama 立即停止底层推理；本次证明的是本地客户端退出与连接关闭。真实 qwen3.5:9b 业务行为、耗时和报告合规性仍待 Day 6；内存中的完整响应缓冲也需结合真实样本观察。
- 初次交付远程 CI 未运行。用户现已授权本次提交、推送和远程 CI，实际结果待执行后补记；Day 3 的历史 CI 不代表本次代码通过远程 CI。

下一步：完成本次已授权的提交、推送与远程 CI；Day 5 的 HTTP 契约、报告校验与错误映射仍等待用户明确启动授权。

## 验收后整理与发布验证（2026-11-10）

- 用户明确表示“Day4我验收通过，提交、推送，远程 CI 本次变更”。仅更新 Day 4 为已验收，不启动 Day 5。
- 按本次要求删除 `docs/evidence` 下全部 21 份编译/测试/运行等过程日志；保留 2 份 Day 2 真实探针 JSON、1 份 Day 3 CI JSON、1 份 Day 4 确定性协议闭环 JSON。可复用的注释检查脚本移至 `scripts/review-java.jsh`，同步修正文档引用。
- AGENTS.md 固化“仅保留必要验收证据、过程日志不归档、临时输出进 target、清理前保留命令与失败/成功摘要”的规则。此前的失败、修复与验证事实仍保留在阶段报告中，未修改业务逻辑、测试断言或固定版本；本次整理不新增无意义 Red。
- 提交前 `mvn -B -ntp verify` 于 2026-11-10 13:34:06（Asia/Shanghai）通过：111 项，0 失败/错误/跳过，JAR 打包成功；完整输出仅临时存于已忽略的 target 目录。
- 远程操作进行前核对 `origin/master` 与本地 HEAD 同为 `9a3573e04632e35473e4a3dbf860fec5d2c255a5`；提交、推送及本次真实 CI 的最终结果随后补记。
