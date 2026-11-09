# Day 2 验收记录

日期：2026-11-09（Asia/Shanghai）。阶段状态以 [plan.md](plan.md) 为准，只有用户能确认验收通过。

## 范围与交付

- 永久机制：AGENTS.md 明确自动读取计划、阶段内自主完成/修复、SDD/TDD/Review/CI、四态状态表与人工验收门禁；禁止擅改已验收产品和架构。Day 1 根据用户确认标为已验收。
- T2：单模块 `pom.xml`、应用入口、`application.properties`、`.gitignore`、`.github/workflows/ci.yml`；固定 Boot 4.1.1 / AI 2.0.1 / Java 17 / Maven 3.8.4，UTF-8；不自动拉模型。
- T3：测试源码中的 ProbeSupport、ChatClientWiringTest、OllamaProbeTest；ApplicationSmokeTest 验证无 Ollama 可启动及配置绑定。探针不注册为 Bean，不打包进入生产 JAR，不属于业务工具。
- docs/product.md 和 docs/architecture.md 仅更新人工验收状态声明，需求和架构决策未变。Day 3–7 未开始。

## Red → Green 与验证

| 命令/检查 | 实际结果 |
| --- | --- |
| `java -version`、`mvn -version` | Oracle JDK 17.0.11、Maven 3.8.4；Maven 使用同一 JDK，系统默认 GBK，工程显式 UTF-8 |
| 首次 `mvn -B -ntp verify` | 因沙箱不能写既有 `D:/Program Files/Java/maven-repository` 缓存而失败；允许写缓存后解析/打包通过。当时尚无测试，不计 Red 或测试通过 |
| `mvn -B -ntp dependency:sources -DincludeGroupIds=org.springframework.ai` | 成功取得所需 2.0.1 API 源码；starter 无 sources 不影响依赖编译 |
| `mvn -B -ntp help:effective-pom -Doutput=target/effective-pom.xml` | 成功；固定 Boot/AI、Java release、编码与测试选择可复核 |
| 接线测试初次运行 | 旧 API `getDefaultOptions()` 导致测试模型选项类型错误；改用固定版本 `getOptions()`，此错误不计预期 Red |
| Red：`mvn -B -ntp -Dtest=ChatClientWiringTest test` | 1 测试、1 断言失败、0 错误：期望首轮仅 1 次模型调用，实际自动循环产生 2 次；日志 `target/day2-red.log` |
| Green：`mvn -B -ntp verify` | 关闭 `AdvisorParams.toolCallingAdvisorAutoRegister(false)`，保留原断言，2/2 通过；日志 `target/day2-green.log` |
| 真实探针首次编译 | builder 旧名称 `defaultOptions` 编译失败；核对 2.0.1 源码后使用 `options`，不计业务 Red |
| 默认：`mvn -B -ntp verify` | 2 测试、0 失败/错误/跳过；未运行 OllamaProbeTest，`!ollama` 标签筛选有效 |
| 真实：`mvn -B -ntp -Pollama-it verify` | 显式 `!ollama \| ollama` 集合，3 测试、0 失败/错误/跳过；真实探针 1/1 通过，无失败样本被重试或舍弃 |

基础配置/文档不伪造 Red；本阶段没有实现解析、知识匹配、业务预算、报告校验或错误映射。上述后续核心逻辑仍须各自执行 TDD。

## 真实 ChatClient 闭环证据

原始结构化记录见 [day2-ollama-probe.json](evidence/day2-ollama-probe.json)。测试再次执行会在 `target/ollama-probe-evidence.json` 生成新记录，不自动覆盖本次归档。

- 本地 Ollama、qwen3.5:9b，temperature=0、think=false、256 token（仅探针），不重试、不拉模型。
- 首轮模型返回 `call_d441fnxg` / `readProbeClock` / `{"label":"Day2"}`；此时实际工具执行次数为 0。
- 宿主核验单个调用、白名单、参数后，经 ToolCallingManager 真正读取时钟，得到 `probe:2026-11-09T09:18:31.586134600Z`，执行次数为 1。
- 第二轮上下文为 USER → ASSISTANT → TOOL，tool response ID 与请求一致，值为上面的实际结果；最终正文精确等于此值，无新增工具调用。
- 2 次模型请求、1 次工具执行、耗时 3,641 ms。脚本化模型记录单独属于确定性测试，不冒充本节真实结果；不保存思考内容。

## Review 与 CI

- 范围：仅 Day 2 基础工程与接线；生产代码仅启动入口，无业务 API 或第三个业务工具，无额外系统/框架。
- 隔离：探针和模型历史均为测试实例私有；确定性测试不需要 Ollama；应用启动测试指向 `127.0.0.1:1`；探针未注册到应用上下文。
- 执行：实际回调前后次数、结构化调用、回传消息 ID/值与最终输出都有断言；自动循环关闭，探针最多两次模型调用、一次工具执行，无重试；连接 3 秒、传输读取 55 秒、测试 120 秒上限，未引入后台 Future 任务。
- 固定 API：使用 2.0.1 的 `getOptions()`、`options(...)`、`disableThinking()`；应用使用元数据中的 `spring.ai.ollama.chat.think=false`，启动测试验证有效，避免未知参数静默失效。
- GitHub Actions：workflow 已建立，待推送并观察真实运行；本地通过不计真实 CI 通过。用户已明确授权本次提交及推送。

## 偏差、风险与门禁

- 实际依赖下载通过现有 Maven 镜像；GitHub Actions 将独立验证网络、固定版本下载与 Linux 环境。
- 真实探针只证明最小接线，不等于业务 A2/A6 或三类异常验收；D4 仍需实现请求级预算/总 deadline/取消/并发限制，D6 才执行业务真实模型矩阵。
- 当前模型探针输出预算 256 token 与 Day 1 一致；生产配置保留架构规定的 2,048 token，未修改已验收决策。
- CI 未取得成功结果前阶段保持进行中；完成后转为待验收并停止。用户验收及启动授权前不得进入 Day 3。
