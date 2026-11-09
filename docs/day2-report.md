# Day 2 验收记录

日期：2026-11-09（Asia/Shanghai）。T2/T3 已完成，当前待验收；阶段状态以 [plan.md](plan.md) 为准，只有用户能确认验收通过。

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
| 最终：`mvn -B -ntp verify` | 2/2 确定性测试通过，0 失败/错误/跳过，构建成功；日志 `target/day2-final-verify.log` |
| 静态 Review | effective POM 版本/编码/标签核对、严格 UTF-8 解码、证据 JSON 解析、`git diff --cached --check` 通过；JAR 中没有探针或测试类 |

基础配置/文档不伪造 Red；本阶段没有实现解析、知识匹配、业务预算、报告校验或错误映射。上述后续核心逻辑仍须各自执行 TDD。

## 2026-11-09 显式类型规范修正

- Specification / Design / Tasks：按用户要求补充长期编码规范，在 T2/T3 当前交付内检查全部 Java 源码；仅替换类型声明及添加导入，不改变已验收产品需求、架构、固定版本或测试断言。
- AGENTS.md 新增长期规则：Java 局部变量必须使用显式类型，禁止 `var`；生产与测试代码均适用，后续自主开发和 Review 持续遵守，无需重复提醒。
- 检查全部 5 个 Java 文件，修正 ApplicationSmokeTest（1 处）、ProbeSupport（1 处）、ChatClientWiringTest（11 处）、OllamaProbeTest（23 处），合计 36 处；生产入口没有违规。
- Red：不适用，本次为无行为变更的类型声明修正，不新增核心确定性逻辑或人为制造业务断言失败。修正前静态扫描发现上述 36 处违规。
- Green / 最终验证：`mvn -B -ntp verify` 成功，4 个测试源码全部重新编译，2 个确定性测试通过，0 失败/错误/跳过；日志 `target/day2-explicit-types-verify.log`。`rg -n '\bvar\b' src --glob '*.java'` 无匹配（退出码 1 为正常零命中）。
- Review：逐项核对显式类型与原表达式及泛型匹配，测试断言和执行流程保持不变；`git diff --check` 通过。未增加依赖或生产工具。
- CI / 风险：本次未提交、推送或触发远程 CI；下文原提交 CI 成功是历史结果，不代表本次修正已通过远程 CI。真实 Ollama 探针本次仅编译、未执行；本地确定性回归已通过。Day 2 修正后重新待验收，未进入 Day 3。

## 显式类型修正补充复验与推送（2026-11-09）

- 用户明确授权提交、推送此次修改并运行远程 CI、重跑真实模型探针；范围仍为 Day 2 T2/T3。
- `mvn -B -ntp -Pollama-it verify`：3 测试、0 失败/错误/跳过；日志 `target/day2-explicit-types-ollama.log`。仅执行一次真实探针，无失败重试或舍弃样本。
- [本次真实探针原始证据](evidence/day2-explicit-types-ollama-probe.json)：首轮结构化调用 `call_470m9uy1` / `readProbeClock` / `{"label":"Day2"}`，宿主真实执行 1 次，结果 `probe:2026-11-09T09:49:14.345525200Z` 回传至第二轮上下文，最终正文完全一致。2 次模型请求，耗时 3,637 ms，PASS。
- 最终 `mvn -B -ntp verify`：2 测试、0 失败/错误/跳过；默认集合排除真实模型，日志 `target/day2-explicit-types-final-verify.log`。
- Review：36 处类型替换未改变测试断言或流程；全源码 `var` 零命中，`git diff --check` 通过。Red 不适用（无行为变更）。
- 远程 CI：待此次提交推送后读取实际运行结果；此前“未提交/未复跑”描述为上次交付时的历史状态。

## 真实 ChatClient 闭环证据（原阶段记录）

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
- GitHub Actions：[运行 42593579283](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42593579283) 已实际完成且 conclusion=success，对应提交 `d5fea2ab47992af5fcc428c57e378f99590c16cd`。JDK 17 配置、Maven 3.8.4 安装及校验、版本断言、`Verify without Ollama`、报告上传均成功；运行于 17:27–17:28（Asia/Shanghai）。
- 推送过程：第一次 HTTPS 推送因认证对话框取消失败；继续执行时发现远程已配置为同一仓库的 SSH 地址，沿用该配置成功推送，没有强制推送或覆盖远程历史。所有提交/推送均在用户本次明确授权内。

## 偏差、风险与门禁

- 本地依赖下载通过现有 Maven 镜像；GitHub Actions 已独立验证固定版本下载、Linux 环境和无 Ollama 默认构建。
- 真实探针只证明最小接线，不等于业务 A2/A6 或三类异常验收；D4 仍需实现请求级预算/总 deadline/取消/并发限制，D6 才执行业务真实模型矩阵。
- 当前模型探针输出预算 256 token 与 Day 1 一致；生产配置保留架构规定的 2,048 token，未修改已验收决策。
- T2/T3 本地验证、Review 与真实 CI 已通过，阶段转为待验收并停止。没有擅改已验收需求或架构；用户验收及启动授权前不得进入 Day 3。
