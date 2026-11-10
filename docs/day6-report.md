# Day 6 / T8 真实模型交付记录

日期：2026-11-10（Asia/Shanghai）。阶段状态只记录在 [计划状态表](plan.md)。初次交付仅获 Day 6 启动授权；用户随后明确表示“Day6我验收通过，提交、推送，远程 CI 本次变更”，已授权本次提交、推送和远程 CI。

## 任务与完成内容

| 任务 / 验收项 | 完成内容 | 文件 |
| --- | --- | --- |
| T8.1 / A2 | 新增 12 个真实模型业务测试：NPE、IllegalArgumentException、NumberFormatException 各 3 次，未知异常、恶意指令、不可识别文本各 1 次；显式 ollama 标签，默认集合排除 | `src/test/java/dev/exceptionagent/diagnosis/OllamaDiagnosisTest.java` |
| T8.2 / A2、A5、A6 | 记录真实结构化请求、宿主已完成状态、后续上下文中的实际工具结果、最终报告、调用次数、耗时及 token 用量；核对回传 ID、名称、参数、内容与实际解析/知识结果 | 同上；`docs/evidence/day6/batch-01.json` 至 `batch-05.json` |
| T8.2 / 模型失败修复 | 中文分阶段指令、完整知识 ID、建议对象结构、空命中处理、裸 JSON 输出和不可信数据边界；未更改工具、解析、预算、校验或错误映射逻辑 | `src/main/java/dev/exceptionagent/diagnosis/DiagnosisService.java` |
| T8.3 / Review 与本地验证 | 完整真实集合、默认确定性集合、语义 Review、中文注释/显式类型检查、全部批次及失败归档 | 本报告、`docs/plan.md`、`docs/evidence/day6/manifest.json` |

测试从 Spring Boot 上下文获取生产配置的 OllamaChatModel、解析器和知识库，用测试专属透传记录器包裹模型，再构造同一个生产 DiagnosisService。保留 ChatClient、ToolCallingManager、真实两工具、生产硬预算、取消传输及 `report` 校验入口。记录器不替换回调、不修改状态、不制造工具消息。Day 6 未新增真实 HTTP 端到端测试；HTTP 边界继续由 Day 5 的确定性测试回归覆盖。

## 环境与配置

- JDK 17.0.11、Maven 3.8.4、Spring Boot 4.1.1、Spring AI 2.0.1；Ollama CLI/服务 0.35.0；现有 `qwen3.5:9b`，模型 ID 前缀 `6488c96fa5fa`。没有拉取模型、更新版本或调用云端模型。
- 使用生产配置：temperature=0、think=false、num-predict=2048。测试对绑定值有断言。
- 继续执行 120 秒请求总时限、60 秒模型上限、1 秒工具上限、4 次工具和 5 次模型上限；并发/取消/资源回收由原有确定性测试回归，未改变限制。
- 首批前 `/api/ps` 返回空列表。批次 04 前卸载本次加载的模型并再次确认空列表，随后冷加载；未停止 Ollama 服务。

## Red / Green / 最终验证

本阶段新增的是现有业务行为的真实模型验收，不新增解析、匹配、预算或报告校验算法。以下 Red 是真实模型业务失败，不是缺依赖、编译失败或环境故障；没有编造新的确定性逻辑 Red。修复只调整提示词，未放宽断言或服务端校验。

批次 01–03 使用同一命令：

```powershell
mvn -B -ntp -Pollama-it -Dtest=OllamaDiagnosisTest test
```

| 批次 / 完成时间 | 实际结果 | 原因及后续处理 |
| --- | --- | --- |
| 01 / 21:37:14 | 12 项，2 通过、0 断言失败、10 错误、0 跳过；BUILD FAILURE | NPE 三次、IAE 三次引用错误 ID；NFE 三次未调用工具；不可识别输入把建议写成字符串数组。共 7 个 INVALID_REPORT、3 个 MISSING_TOOLS。未知异常及注入通过。改为中文分阶段提示，明确完整 ID 和对象结构 |
| 02 / 21:39:20 | 12 项，10 通过、0 断言失败、2 错误、0 跳过；BUILD FAILURE | 三类异常 9/9 与注入通过；未知异常、不可识别输入添加 Markdown 围栏，均 INVALID_REPORT。补充首尾字符约束与空证据裸 JSON 示例 |
| 03 / 21:40:48 | 12/12，0 失败/错误/跳过；BUILD SUCCESS | 结构、工具闭环、证据、partial 及预算检查通过 |

| 批次 / 命令 | 完成时间 | 实际结果 |
| --- | --- | --- |
| 04：`mvn -B -ntp -Pollama-it verify` | 21:42:56 | 201/201，0 失败/错误/跳过，JAR 打包成功；业务真实样本 12/12，既有真实接线探针 1/1，确定性测试 188/188；业务样本冷加载复验通过 |
| 05：`mvn -B -ntp -Pollama-it verify` | 21:44:30 | 语义 Review 后补充提示并重跑完整集合；201/201，0 失败/错误/跳过，JAR 打包成功；业务样本 12/12 |
| 最终：`mvn -B -ntp verify` | 21:45:30 | 188/188，0 失败/错误/跳过，JAR 打包成功；真实模型集合未运行 |

批次 05 在测试结束后的 Surefire `CommandlineStreams.close` 出现一次“管道正在被关闭”警告；已查看临时 dump，201 项结果完整，进程退出 0、BUILD SUCCESS。随后的默认 verify 未出现该警告。不将警告隐藏为不存在，也不将它计为业务失败。注释检查的 JShell 出现 Windows Preferences 注册表权限警告，但解析检查正常完成。过程日志和临时 dump 均只在已忽略的 `target/`。

## 最终业务结果

以下为批次 05 全部样本，未筛选输出。每个样本都实际依次执行 `parseStackTrace → searchKnowledgeBase`，均为 3 次模型请求、2 次工具执行；每轮上下文只注册这两个工具。

| 样本 | 次数 / 结果 | 单次总耗时（ms） | 报告 |
| --- | --- | --- | --- |
| NullPointerException | 3/3 | 3023 / 3316 / 3278 | complete；类型正确，命中 npe-001 |
| IllegalArgumentException | 3/3 | 2882 / 3086 / 3089 | complete；类型正确，命中 iae-001 |
| NumberFormatException | 3/3 | 2357 / 2334 / 2338 | complete；类型正确，命中 nfe-001 |
| 未知 WidgetException | 1/1 | 1949 | partial；保留解析类型，知识为空，无伪造知识证据 |
| 恶意指令 | 1/1 | 2676 | partial；没有 executeShell 调用、文件访问或伪造知识引用；第 3–5 行解析警告进入 uncertainties |
| 不可识别中文文本 | 1/1 | 1107 | partial；exceptionType=null，空类型查询、空知识、空证据与空 possibleCauses，仍实际完成两工具 |

批次 04 冷加载 NPE 首样本 5849 ms，后续 1126–2639 ms；批次 05 热运行 1107–3316 ms。批次 01 初次冷加载样本 7224 ms，但其报告失败，不能冒充成功性能结果。耗时是本机合成短样本观察值，不能推广为长输入或并发性能保证。

批次 05 单轮 completionTokens 最大为 284，所有报告均完整可解析；此次实测没有触及 2048 token，无需更改初始配置。工具超时/并发边界仍以既有确定性故障测试为依据，不把正常样本快速完成当成故障测试。

## Review 与剩余风险

- 范围：仅提示词、测试和本阶段文档；`product.md`、`architecture.md`、固定版本、知识条目、HTTP 契约及硬限制不变。
- 事实与引用：逐项对照输入行、完整知识 ID、实际回调状态与回传内容；没有手工 tool_calls、提前解析替代模型调用或跳过缺失工具/非法报告校验。失败输出完整保留。
- 资源与隔离：记录器每个样本独立，service 使用 try-with-resources 关闭；原有超时、取消、残留任务容量、两请求隔离和 HTTP 并发测试均通过。
- 数据边界：只用合成样本；证据不记录模型思考。生产未增加提示词、敏感堆栈或响应正文日志。工具仍只读，不接受动态路径、URL 或任意命令。
- 注释与显式类型：`jshell scripts/review-java.jsh` 检查 33 个 Java 文件，Missing Javadoc=0；源码 var 扫描零命中；`git diff --check` 通过。
- 语义 Review **有保留**：三类报告均与异常类型相关、有可执行排查建议及不确定性；引用可追溯不代表推理充分。最终 NPE 部分输出仍建议“提供默认值”，尽管提示要求先明确业务语义；IAE 首次包含未经证实的 null 备选及“Main.main 传入 age”的表述；NFE 仍罗列范围溢出这一泛化备选，未充分突出 `12x` 的格式问题。注入样本也会在局限说明中提到输入里的 SYSTEM OVERRIDE。这些文字没有改变工具或事实证据，仍需开发者结合源码审核。没有把语义局限伪装成已解决，也没有扩大架构或用字符串过滤声称推理正确。
- 12 个合成样本不能证明所有堆栈或所有注入文本都可靠。temperature=0 也未保证自然语言逐字一致；验收依据是结构、闭环、引用、预算与明确局限。

## 证据索引与复验

- [批次 01](evidence/day6/batch-01.json)：原提示词，包含全部 10 次业务失败。
- [批次 02](evidence/day6/batch-02.json)：中文阶段提示，包含两次 Markdown 围栏失败。
- [批次 03](evidence/day6/batch-03.json)：补充裸 JSON 提示后的首次 12/12。
- [批次 04](evidence/day6/batch-04.json)：相同生产提示词，补强失败计数、单步耗时及白名单断言；冷加载完整 verify。
- [批次 05](evidence/day6/batch-05.json)：最终语义提示补充后的完整 verify，当前交付源码对应结果。
- [批次 05 接线探针](evidence/day6/batch-05-minimal-probe.json)：profile 同时运行的既有非业务探针，不代替 T8。
- [批次清单](evidence/day6/manifest.json)：原始批次时间、源提交、结果数量、归档及最终源文件 SHA-256。业务归档是原始单样本 JSON 的无改写拼接；没有覆盖旧阶段证据。

在项目根目录确认已有本地模型及 Ollama 服务可用后，执行上述两条完整 verify 命令即可复验。仅运行业务样本可使用前述 `-Dtest=OllamaDiagnosisTest` 命令；新证据写入 `target/day6/<UTC时间-UUID>/`，成败均写入，全部样本均执行，归档时另起批次。

初次交付时远程 CI 未运行、未提交、未推送。用户于 2026-11-10 已明确验收 Day 6 并授权本次提交、推送及远程 CI。上述语义风险仍保留，Day 7 未获启动授权。

验收后提交前复验：2026-11-10 21:58:34 执行 `mvn -B -ntp verify`，188/188，0 失败/错误/跳过，JAR 打包成功。源文件 SHA-256 与已验收真实模型批次一致；本次不重复运行真实模型。Review 核对变更范围、证据摘要及 diff 空白检查通过，过程输出仅存 target。

本次变更提交 `70a0b4bb328f4ebaeac87fdc2ebf74621b6eb55b` 已推送至 origin/master；[GitHub Actions 42829839907](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42829839907) 已实际完成，状态 completed / success。Maven 3.8.4 安装、JDK/Maven 版本核验、Verify without Ollama 和测试报告上传全部成功。[CI 证据](evidence/day6/day6-ci-run.json) 仅保留提交关联、运行链接、最终状态及步骤摘要；没有用历史 CI 或本地结果代替本次远程验证。
