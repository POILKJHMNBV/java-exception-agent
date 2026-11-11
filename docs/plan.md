# Java Exception Agent：一周执行计划

## 阶段状态与人工门禁

状态规则见 [AGENTS.md](../AGENTS.md)。阶段状态与测试结果分开；仅用户能确认已验收。

| 阶段 | 任务 | 状态 | 人工验收/执行依据 |
| --- | --- | --- | --- |
| Day 1 | T1 | 已验收 | 用户于 2026-11-09 明确表示“Day 1 已验收通过” |
| Day 2 | T2、T3 | 已验收 | 用户于 2026-11-09 明确表示“Day2我验收通过，现在进行Day3” |
| Day 3 | T4、T5 | 已验收 | 用户于 2026-11-10 明确表示“Day3我验收通过，现在进行Day4” |
| Day 4 | T6 | 已验收 | 用户于 2026-11-10 明确表示“Day4我验收通过”，并授权提交、推送和远程 CI 当次变更；随后再次确认验收并授权启动 Day 5 |
| Day 5 | T7 | 已验收 | 用户于 2026-11-10 明确表示“Day5我验收通过，提交、推送，远程 CI 本次变更”；授权仅限本次 Day 5 交付，未授权启动 Day 6 |
| Day 6 | T8 | 已验收 | 用户于 2026-11-10 明确表示“Day6我验收通过，提交、推送，远程 CI 本次变更”；授权仅限本次 Day 6 交付，未授权启动 Day 7 |
| Day 7 | T9 | 已验收 | 用户于 2026-11-11 明确表示“Day7我验收通过，提交、推送，远程 CI 本次变更”；授权限本次 Day 7 交付 |

### Day 7 执行范围（2026-11-10）

- Specification：T9/A8，依赖已验收 T8；逐项复核 A1–A7、既有 Red/Green 与真实模型/CI 证据，交付可复现的启动、curl 和故障说明。
- Design：保持已验收产品、架构、固定版本及硬预算；通过 README、验收矩阵和本阶段报告建立交付入口，复用既有测试与证据。若 Review 发现确定性缺陷，先建立有效断言 Red，再最小修复和回归。
- Tasks：T9.1 全链路代码与 A1–A8 证据 Review；T9.2 本地启动/curl/故障演示及使用说明；T9.3 最终确定性 verify、文档与证据检查、交付报告。
- 门禁：初次交付未授权提交、推送或远程 CI；用户于 2026-11-11 验收后明确授权本次操作。Day 6 历史 CI 仅作为历史证据，不冒充 Day 7 新 CI。

### Day 7 执行记录

- T9.1：完成 [A1–A8 验收矩阵](acceptance.md)，逐项关联实现、测试、历史 Red/Green、真实闭环及 CI。复核两工具白名单、参数/顺序/预算、单调 deadline、真实取消及容量回收、请求隔离、严格输入/报告引用、只读数据和资源释放；未发现本阶段需修复的确定性业务缺陷。单 Agent 自查，未委派。
- T9.2：新增 [README](../README.md) 及五份 examples 合成请求，提供固定环境、启动、curl、响应解读、全部错误分类、故障复验与已知局限。打包应用真实模型 HTTP 演示 9/9：三类异常 complete、未知/不可识别 partial，以及 400/413/415/503；原始响应保留于 [HTTP 批次 01](evidence/day7/http-smoke-01.json)，未增加逐工具观测，不冒充新的 A2 闭环证据。全部本次应用进程已停止，现有 Ollama 未被停止。
- T9.3：`mvn -B -ntp verify` 于 2026-11-10 22:14:19 通过，188/188、0 失败/错误/跳过，JAR 打包成功；后续仅文档/样例/响应归档变更。33 个 Java 文件 Missing Javadoc=0，var 零命中；文档 UTF-8、围栏、链接、空白及 JSON/证据目录检查通过。Day 6 五批哈希/成败数量、四个最终源文件哈希及批次 05 的 12 条调用→宿主→回传→报告序列复核通过。
- 命令草稿发现 PowerShell 将未引用的 `-Dfile.encoding=UTF-8` 误解析为主类参数；加引号后 Java 版本检查及完整前台启动/curl 故障命令复验通过。此失败属于文档命令，不计业务 Red。JShell 本机注册表偏好警告不影响注释检查。历史失败与命令均保留，详情见 [Day 7 报告](day7-report.md)。
- 风险：本次 NPE/IAE 仍有未经源码证实的参数来源/null 备选，NFE 泛化列出范围溢出；引用有效不保证推理正确。维持 Day 6 语义局限及本机使用边界，没有放宽要求或更改提示词。完整真实 profile 本阶段未重跑，真实闭环复用已验收且哈希一致的 Day 6 记录，并增加五种真实 HTTP 复验。
- 初次交付未提交、未推送、远程 CI 未运行。Day 6 CI 42829839907 success 仅为历史结果；当前源码、测试、POM、工作流与其提交一致。用户于 2026-11-11 明确验收 Day 7 并授权本次提交、推送和远程 CI。
- 验收后提交前复验：2026-11-11 13:19:17（Asia/Shanghai）执行 `mvn -B -ntp verify`，188/188、0 失败/错误/跳过，JAR 打包成功。源文件与已验收模型证据哈希一致，未重复运行真实模型。远程 master 与本地基线同为 `0a624171cf45c43161789c755e8a1be50bf9a87d`；提交及本次 CI 结果待执行后记录。

### Day 6 执行范围（2026-11-10）

- Specification：T8/A2/A5/A6，依赖已验收 T7；NPE、IllegalArgumentException、NumberFormatException 各 3 次，未知异常与恶意指令各 1 次，额外覆盖无法识别的合法字符串。
- Design：测试专用记录包装器透传生产配置的真实 Ollama 模型，保留生产 ChatClient、有界循环、ToolCallingManager、两业务工具及报告校验。记录原始结构化调用、实际请求状态、进入后续模型上下文的工具结果、最终报告与耗时；只用合成输入，不记录思考内容。
- Tasks：T8.1 建立显式 ollama 标签的业务验收集合及证据记录；T8.2 执行全部样本并定位修复失败（确定性逻辑修复先 Red）；T8.3 人工式语义 Review、默认 verify、证据归档与交付报告。
- 初次交付未授权提交、推送或远程 CI；用户于 2026-11-10 验收后明确授权本次提交、推送及远程 CI，不启动 Day 7。保持固定版本、2,048 token 及已验收硬预算。

### Day 6 执行记录

- 验收后提交前复验：2026-11-10 21:58:34 执行 `mvn -B -ntp verify`，188/188，0 失败/错误/跳过，JAR 打包成功；当前源文件与已验收真实模型证据的 SHA-256 一致，本次不重复运行真实模型。
- T8.1/A2：新增 `OllamaDiagnosisTest`，保留生产配置的真实模型、ChatClient、有界服务、ToolCallingManager 和实际两工具；三类异常各 3 次，未知异常、恶意指令、不可识别文本各 1 次。逐样本记录结构化调用、实际宿主状态、工具结果回传与最终报告；默认 ollama 标签排除。
- T8.2：全量批次 01 为 2/12（7 个 INVALID_REPORT、3 个 MISSING_TOOLS），批次 02 为 10/12（未知异常及不可识别文本输出 Markdown 围栏），没有跳过或挑选样本。仅修正生产提示词的阶段指令、完整知识 ID、对象结构和裸 JSON 格式；批次 03 首次 12/12。校验、预算、版本及已验收规格/架构不变。
- T8.3：批次 04 冷加载完整 `mvn -B -ntp -Pollama-it verify` 201/201，冷加载首样本 5849 ms。语义 Review 后补充提示并执行批次 05，2026-11-10 21:44:30 完整 profile 再次 201/201，0 失败/错误/跳过；业务样本 12/12、既有真实探针 1/1、确定性集合 188/188。最终业务样本均 3 次模型请求、2 次工具执行，1107–3316 ms，最大单轮 completionTokens=284，保留 2048 token 设置。
- 最终 `mvn -B -ntp verify` 于 2026-11-10 21:45:30 通过，188/188、0 失败/错误/跳过，JAR 打包成功，真实集合未运行。33 个 Java 文件 Javadoc 无缺失，var 零命中，diff 空白检查通过。批次 05 测试完成后的 Surefire 关闭管道警告已核对，不影响完整结果和成功退出，详情见报告。
- Review：调用闭环、证据、partial、白名单、预算和既有资源/隔离测试通过。语义仍有局限：部分 NPE 建议未经业务确认使用默认值，IAE 有未经验证的参数备选，NFE 有泛化的范围备选；引用正确不保证推理正确，需开发者审阅。未将提示词改进声称为彻底消除模型错误。
- [Day 6 交付记录](day6-report.md) 保留全部命令、失败、语义 Review 与风险；[批次清单](evidence/day6/manifest.json) 关联五个完整业务批次和最终源文件摘要。证据仅归档必要闭环 JSON，过程日志仅在 target。初次交付未提交、未推送，远程 CI 未运行；用户于 2026-11-10 明确验收并授权后，提交 `70a0b4bb328f4ebaeac87fdc2ebf74621b6eb55b` 已推送至 origin/master，[真实 CI 42829839907](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42829839907) completed / success；固定 Maven/JDK 核验、无 Ollama 的 verify 及报告上传均通过。[CI 结构化证据](evidence/day6/day6-ci-run.json) 已归档，不启动 Day 7。

### Day 5 执行范围（2026-11-10）

- Specification：T7/A1/A5，依赖已验收 T6。覆盖 JSON/类型/空值、16,000 Unicode 码点/200 行/128 KiB 请求体边界，完整与 partial 报告、引用伪造、非法报告、错误映射和请求隔离。
- Design：api 包负责请求体限额、严格输入验证、统一关联 ID 和错误响应；diagnosis 包按实际解析及知识结果生成证据目录，模型仅提供带目录引用的解释、建议、不确定性，服务端核验和组装报告。保留现有 ChatClient/ToolCallingManager/真实业务工具及硬预算，不变更固定版本或已验收需求与架构。
- Tasks：T7.1 报告契约与校验 Red→Green；T7.2 HTTP 输入/错误映射/隔离 Red→Green；T7.3 全链路 Review、最终 verify 与阶段报告。
- CI 边界：初次交付未授权提交、推送或远程 CI；用户于 2026-11-10 验收后明确授权本次提交、推送及远程 CI。真实模型业务验收属于 Day 6，不提前启动。

### Day 5 执行记录

- T7.1/A5：新增不可变报告和严格校验器，由实际解析主链/帧与本次知识命中组装证据，模型只提供解释、建议、不确定性。拒绝错误结构、重复字段、尾随 JSON、缺失或伪造引用；类型、状态和事实字段不接受模型指定。不可识别/未命中/解析警告自动 partial。30 项测试有效 Red 为 30 断言失败，Green 30/30。
- T7.2/A1：POST /api/diagnoses、严格 JSON/类型/空值校验、16,000 Unicode 码点/200 行/128 KiB 原始正文限制、统一错误及请求 ID。MockMvc 保留真实编排和两工具，仅替换模型。43 项首次有效 Red 为 HTTP 404 断言失败，Green 43/43；Review 增加实际并发、引用拒绝后的容量回收和异常链脱敏，46/46。
- T7.3：实际内嵌服务器验证 HTTP/1.1 chunked、精确 128 KiB 可接收且多一字节 413，1/1。最终 `mvn -B -ntp verify` 于 2026-11-10 15:26:20（Asia/Shanghai）通过：188 项、0 失败/错误/跳过，JAR 打包成功，其中本阶段新增 77 项；不依赖 Ollama。
- Review：输入/报告边界、引用真实性、两请求交错隔离、两槽位实际满载 503、失败归还容量、总 deadline 内报告校验、敏感错误脱敏、只读数据边界均通过；既有预算/超时/取消测试回归通过。32 个 Java 文件注释检查缺失 0，var 零命中，diff 空白检查通过。固定版本和已验收需求/架构不变。
- 初次交付时未提交、未推送，远程 CI 和真实模型测试均未运行。用户于 2026-11-10 验收并明确授权后，提交 `9ac9e8be1291ce6cfaf82c301752426c0d4f2c6c` 已推送至 origin/master，[真实 CI 42818708943](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42818708943) 实际 completed / success；Maven 3.8.4 安装、JDK/Maven 版本核验、无 Ollama 的 verify 和测试报告上传均通过。[结构化证据](evidence/day5/day5-ci-run.json) 仅保留提交关联、最终状态和步骤摘要，过程输出仍仅存 target。完整任务/文件、早期占位与测试草稿失败、Red/Green/最终命令和剩余风险见 [Day 5 交付记录](day5-report.md)。真实模型业务测试未运行，未启动 Day 6。
- 验收后提交前复验：2026-11-10 20:43:45（Asia/Shanghai）执行 `mvn -B -ntp verify`，188/188，0 失败/错误/跳过，JAR 打包成功；过程输出仅存 target，不归档为证据。

### Day 4 执行范围（2026-11-10）

- Specification：T6/A6，依赖已完成的 T3、T4、T5。脚本模型经真实 ChatClient、ToolCallingManager 和两个业务工具完成调用闭环；验证批量/重复调用、4 次工具/5 次模型硬预算、非法参数/白名单/越序/缺失工具、失败、单步/总超时、取消和并发容量。
- Design：diagnosis 包内实现请求私有状态与有界循环；工具从 ToolContext 读取本次原始输入，只读复用 Day 3 组件。执行前校验、单调 deadline、有界无等待执行器与可取消传输共同约束资源。返回本次解析、知识命中和未校验模型文本供 Day 5 组装报告，不提前实现 HTTP 或报告校验。
- Tasks：T6.1 固定版本 API/传输核对及测试契约；T6.2 编排/参数/预算 Red→Green；T6.3 超时/取消/容量/隔离 Red→Green；T6.4 Review、最终 verify、证据及验收记录。
- CI 边界：初次本地交付未运行远程 CI；用户于 2026-11-10 验收 Day 4 后明确授权提交、推送并运行本次远程 CI。此授权不跨后续交付，不启动 Day 5。

### Day 4 执行记录

- T6.1/T6.2：新增请求私有 ToolContext、两工具严格 JSON/白名单/阶段校验、真实 ChatClient → ToolCallingManager → 业务回调闭环。批量逐个执行，重复调用计入总量；4 次工具和 5 次模型分别硬限制。28 项编排测试首次 Red 为 28 个断言失败，最小实现后全部通过。
- T6.3：单调总时限 120 秒、模型 60 秒、工具 1 秒，每步取剩余总时限与单步上限的较小值；两个请求槽位、两个工作线程和容量为 2 的内部步骤交接队列，不排队等待外部请求。超时/调用方中断取消 Future；不合作的残留任务实际退出前不归还请求容量。5 项取消/容量测试首次 Red 为 5 个断言失败，随后通过；Review 补充双请求交错隔离测试通过。
- 传输核验：固定版本源码确认默认模型重试需关闭。故障服务首次测试 5 项中 2 断言失败、1 个部分正文取消未关闭连接的超时错误、2 项通过。关闭重试并使用覆盖完整正文的 JDK 可中断传输后 5/5；Review 再补完整 UTF-8 Ollama 协议闭环，6/6 通过。连接上限 3 秒，取消测试覆盖响应头前和部分正文两种状态，对端均观察到 EOF。
- T6.4 最终 `mvn -B -ntp verify`：2026-11-10 12:54（Asia/Shanghai），111 项、0 失败/错误/跳过，JAR 打包成功；新增 Day 4 确定性测试 40 项。固定版本和已验收需求/架构均未修改。测试使用脚本模型或本地 HTTP 夹具，不依赖 Ollama。
- Review：执行前参数/顺序/预算、总 deadline 不重置、实际取消、残留容量、两请求隔离、数据不执行、默认不记录原始堆栈、显式类型和中文注释检查通过。23 个 Java 文件中具名类型/字段/方法及 record 组件 Javadoc 检查无缺失，匿名测试类另行人工检查；`var` 零命中，`git diff --check` 通过。
- [确定性协议闭环证据](evidence/day4/t6-protocol-loop.json)：3 次模型请求、2 次真实工具执行，结构化调用与实际结果回传可追溯，最终文本等于实际知识结果。本证据不是 Ollama 推理或 A2/A5 验收；真实模型测试本次未运行，报告与 HTTP 契约仍属 Day 5。
- 初次本地交付时未提交、未推送、远程 CI 未运行；用户验收并授权后，提交 `36704b170ea00780c49609a3645cc334338a71e0` 已推送至 `origin/master`，[真实 CI 42763718131](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42763718131) 实际 completed / success，固定 Maven/JDK 版本校验、无 Ollama 的 verify 和报告上传均通过；[结构化证据](evidence/day4/day4-ci-run.json)。详细任务、变更文件、Red/Green/最终命令和风险见 [Day 4 交付记录](day4-report.md)。
- 2026-11-10 按用户要求精简全部阶段证据：删除编译/测试/运行等过程日志，保留阶段报告中的命令、失败原因、结果与历史事实；保留必要闭环/CI JSON，将可复用注释检查脚本移至 `scripts/`，同步清理引用并在 AGENTS.md 固化规则。本次整理不改变业务代码和测试断言，无需新增业务 Red。
- 验收后提交前复验：2026-11-10 13:34:06（Asia/Shanghai）执行 `mvn -B -ntp verify`，111/111，0 失败/错误/跳过，JAR 打包成功；过程输出仅临时存于 target，不重新归档。

### Day 3 执行范围（2026-11-09）

- Specification：T4/A3 的主异常链、最深主因、来源行号、最多 5 个关键帧与解析警告；T5/A4 的三类知识条目、精确匹配、稳定排序、空命中和加载/查询校验。
- Design：遵循已验收架构，在 tools 包实现无状态解析器和启动加载的不可变 JSON 知识库。解析结果保留原始行作为后续证据依据；查询显式携带本次解析结果，校验查询类型一致。Day 4 再接入请求 ToolContext、两个模型工具入口及预算循环；Day 3 不注册模型回调、不实现 API/报告。
- Tasks：T4.1 输出契约与边界测试 Red；T4.2 解析 Green/Review；T5.1 加载、匹配、非法查询测试 Red；T5.2 JSON 资源与实现 Green；T4/T5 最终 verify、Review、真实 CI 状态与验收报告。
- CI 边界：用户于 2026-11-09 明确授权提交、推送本次 Day 3 修改并运行远程 CI；本次授权不跨后续交付。后续默认先完成本地验证并等待人工验收，不自动提交、推送或运行远程 CI。

### Day 3 执行记录

- T4.1/T4.2：新增不可变解析结果与无状态解析器。覆盖三类样例、主因链、最多 5 个关键帧及父帧补齐、线程/模块/类加载器前缀、特殊位置、空消息、CRLF/LF、Suppressed、省略帧、无法识别行及跨解析隔离。首次 Red：21 项中 17 断言失败、4 空结果错误；Green：21/21。
- T4 Review 回归：补测复现消息空白丢失、损坏 Caused by 后帧归属错误、长全限定名触发正则 StackOverflowError（2 失败、1 错误）。保留原消息空白、停止不可信帧归属、改为 Unicode 码点迭代检查后，24/24 通过。
- T5.1/T5.2：新增三类 JSON 知识条目与启动加载 Bean；必填字段/数组、JSON 重复字段/尾随文档、唯一 ID 校验；按 ID 排序，精确匹配最多 3 项；查询必须等于本次解析主因（不可识别时仅空字符串）。首次 Red：45 项中 41 断言失败、4 空命中用例通过；Green：45/45。坏库阻止上下文启动、资源关闭及结果深层不可变均有测试。
- 最终本地 `mvn -B -ntp verify`：2026-11-09 21:19（Asia/Shanghai），71 项、0 失败/错误/跳过，JAR 打包成功。固定版本不变；默认排除 ollama，启动测试指向不可达 Ollama 地址。
- Review：范围、证据行号、只读数据边界、资源关闭、请求状态隔离、Java 显式类型检查通过；本阶段不实现 Day 4 预算/超时调度或 Day 5 HTTP/报告，不声称业务工具调用闭环已通过。
- 2026-11-09 用户要求的修正：10 份证据按所属阶段迁入 `docs/evidence/day2/`、`docs/evidence/day3/`，内容 SHA-256 保持一致；同步文档链接。11 个 Java 文件补充中文 Javadoc 和关键片段注释，修改前后非注释词法序列完全一致。AGENTS.md 固化注释、证据归档及先验收再授权提交/推送/远程 CI 的默认规则。
- 注释修正后 `mvn -B -ntp verify`：2026-11-09 23:39（Asia/Shanghai），71/71，0 失败/错误/跳过；过程日志已清理，结果见本节摘要。本次无业务行为变更，不新增无意义 Red；原 T4/T5 Red/Green 证据保留。
- 本次真实 GitHub Actions CI：提交 `7bc2efd2bd4b4948f7f99ac9575ed9f701ae9e12` 已推送到 `origin/master`，[CI 42647624769](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42647624769) 实际 completed / success，Maven 3.8.4 版本校验及无 Ollama 的 verify 步骤通过；[归档证据](evidence/day3/day3-ci-run.json)。详情与日志见 [Day 3 交付记录](day3-report.md)。

### Day 2 执行范围（2026-11-09）

- Specification：T2 的固定版本构建、默认确定性测试与真实 CI；T3 的 ChatClient → ToolCallingManager → 真实探针回调 → 模型最终输出闭环。对应 A7/A8 的基础设施部分，不计业务 A2 通过。
- Design：单 Maven 模块、Java 17/UTF-8；探针只存在于测试源码，不注册生产工具；显式关闭自动工具循环。真实模型测试用 ollama 标签及 ollama-it profile 隔离。
- Tasks：T2.1 建立 POM/应用入口/本地配置；T2.2 默认离线模型测试与 GitHub Actions；T2.3 固定版本解析、effective POM、verify；T3.1 按固定版本 API 建立脚本化模型接线断言；T3.2 本地真实模型探针及闭环证据；T2/T3 Review 与验收报告。
- CI 授权：用户于 2026-11-09 提供 `https://github.com/POILKJHMNBV/java-exception-agent`，明确授权提交并推送本次 Day 2 变更以运行 CI。仅限本次阶段，后续阶段仍遵守默认不自动提交/发布规则。

### Day 2 执行记录

- 2026-11-09 远程复验完成：显式类型修正与本次探针证据已提交并推送至 `origin/master`，提交 `b99e45514e3be7609d6d734da769db11a98a5f69`；[CI 42596648824](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42596648824) 实际状态 completed / success。Day 2 恢复待验收；以下“待推送/未执行”描述保留为之前各次操作的历史记录，不代表当前状态。
- 2026-11-09 补充复验：用户明确授权提交、推送本次修改、运行远程 CI、重跑真实模型探针。`mvn -B -ntp -Pollama-it verify` 3/3 通过，探针 2 次模型请求、1 次工具执行、3,637 ms，闭环证据已另存 `docs/evidence/day2/day2-explicit-types-ollama-probe.json`；随后默认 `mvn -B -ntp verify` 2/2 通过。Review 和源码 `var` 扫描通过；远程 CI 待推送后核验。
- 2026-11-09 编码规范修正：按用户要求在 AGENTS.md 固化 Java 局部变量显式类型规则；扫描当前阶段全部 5 个 Java 文件，替换 4 个测试文件中的 36 处 `var`。本次属于 T2/T3 无行为变更修正，生产入口无违规；全源码扫描零命中，`mvn -B -ntp verify` 2/2 通过，Review 与 `git diff --check` 通过。本次未提交/推送，未运行新的远程 CI 或真实模型探针；原有结果保留为历史证据。修正后重新待验收，详情见验收记录补充。
- T2.1/T2.3：固定版本 POM、Java 17/UTF-8、应用入口和本机 Ollama 配置已建立；依赖解析、编译和可执行 JAR 打包通过。effective POM 已生成并核对。
- T2.2：GitHub Actions 已配置 JDK 17、校验 SHA-512 后安装 Maven 3.8.4、打印并检查版本、执行默认 verify、上传测试报告；[真实 CI 42593579283](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42593579283) 于 2026-11-09 17:28（Asia/Shanghai）通过，提交 `d5fea2ab47992af5fcc428c57e378f99590c16cd`。
- T3.1：确定性接线测试保持真实 ChatClient、ToolCallingManager 与探针回调；默认自动循环导致断言 Red，关闭后 Green。应用启动测试指向不可达 Ollama 地址，验证启动不需要模型服务并核对配置绑定。
- T3.2：`mvn -B -ntp -Pollama-it verify` 已通过，3 个测试，0 失败/错误/跳过；真实模型探针 1/1，2 次模型请求、1 次工具执行，耗时 3,641 ms。默认 `mvn -B -ntp verify` 仅运行 2 个确定性测试，0 失败/错误/跳过。
- 详细命令、失败记录、Review 与闭环证据见 [Day 2 验收记录](day2-report.md)。T2/T3 已于 2026-11-09 经用户验收；Day 3 已获启动授权。

## Day 1 实测记录（2026-11-09，Asia/Shanghai）

| 检查 | 结果 |
| --- | --- |
| 仓库 | 初始仅 .git、无提交、无业务代码；未发现上级 AGENTS.md |
| `java -version` / `javac -version` / `mvn -version` | Oracle JDK 与 javac 均 17.0.11；Maven 3.8.4，使用同一 JDK；默认编码 GBK，工程须显式 UTF-8 |
| `ollama --version` / `/api/version` | CLI 与服务均 0.35.0；localhost:11434 可达 |
| `ollama list` / `ollama show qwen3.5:9b` | 已安装；ID 6488c96fa5fa，6.6 GB，Q4_K_M；声明 tools / thinking，默认 thinking=true |
| 固定框架版本 | 官方固定 tag 支持 Java 17 与 Boot 4.1.1；未升级或安装依赖 |
| Maven Central 直连检查 | 本次终端请求被代理 127.0.0.1:9 拒绝；尚未运行 Maven 依赖解析或编译，不能判断用户终端或 CI 也不可达 |
| 原生 Tool Calling | 下述只读探针连续 3/3 通过，单次两轮总耗时 9,373 / 4,795 / 4,811 ms |
| Java/Spring AI 集成、业务工具、CI | 尚未实现或运行，不计为通过 |

探针使用 Ollama `/api/chat`、stream=false、think=false、temperature=0、num_predict=256，每次 HTTP 超时 60 秒。
模型先返回 `message.tool_calls`，名称为 readProbeClock、参数为 `{"label":"Day1"}`；宿主校验后真正读取本地 UTC 时钟，再以 role=tool 回传。第二轮保留第一轮 assistant 消息和工具定义，验证不再调用且正文精确等于实际返回值。

| 次数 | 工具调用 ID | 工具结果与最终正文（完全一致） |
| --- | --- | --- |
| 1 | call_r52clja7 | probe:639298099873787252 |
| 2 | call_rbio9r4o | probe:639298099921093711 |
| 3 | call_688775qi | probe:639298099969080350 |

结论：此模型在本机、上述参数下通过最小原生协议闭环；未证明中文复杂参数、业务链、JSON 报告或 Spring AI 集成稳定。readProbeClock 是临时环境探针，不进入应用，不增加 MVP 工具数量。

## 人工复验：PowerShell 原生协议探针

先执行 `java -version`、`mvn -version`、`ollama --version`、`ollama list`、`ollama show qwen3.5:9b`。确认服务已启动后，在 PowerShell 粘贴以下代码；不下载模型、不写业务文件。最多三个样本，任一失败即停；纯文本宣称调用不算通过。

```powershell
$ErrorActionPreference = 'Stop'
function Read-ProbeClock([string]$label) {
  @{ label=$label; stamp=('probe:' + [DateTime]::UtcNow.Ticks.ToString()) }
}
$tool = @{ type='function'; function=@{
  name='readProbeClock'
  description='Read the actual local clock and return an opaque stamp. Never invent its value.'
  parameters=@{ type='object'; properties=@{
    label=@{type='string'; enum=@('Day1')}
  }; required=@('label'); additionalProperties=$false }
} }
function Invoke-ProbeChat($body) {
  Invoke-RestMethod -Uri 'http://localhost:11434/api/chat' -Method Post `
    -ContentType 'application/json' -TimeoutSec 60 `
    -Body ($body | ConvertTo-Json -Depth 20 -Compress)
}
foreach ($trial in 1..3) {
  $watch = [Diagnostics.Stopwatch]::StartNew()
  $body = @{ model='qwen3.5:9b'; stream=$false; think=$false
    options=@{temperature=0; num_predict=256}; tools=@($tool)
    messages=@(@{role='user'; content='Call readProbeClock exactly once with label Day1. After receiving the tool result, reply with only the exact stamp string. Do not invent a stamp.'})
  }
  $first = Invoke-ProbeChat $body
  $first.message | ConvertTo-Json -Depth 15 -Compress
  $calls = @($first.message.tool_calls)
  if ($calls.Count -ne 1 -or $calls[0].function.name -ne 'readProbeClock') {
    throw "Trial $trial failed: missing or wrong structured tool call"
  }
  $arg = $calls[0].function.arguments
  if ($arg.label -cne 'Day1' -or @($arg.PSObject.Properties).Count -ne 1) {
    throw "Trial $trial failed: invalid arguments"
  }
  $actual = Read-ProbeClock $arg.label
  $actual | ConvertTo-Json -Compress
  $body.messages += $first.message
  $body.messages += @{role='tool'; tool_name='readProbeClock';
    content=($actual | ConvertTo-Json -Compress)}
  $final = Invoke-ProbeChat $body
  $final.message | ConvertTo-Json -Depth 15 -Compress
  if ($null -ne $final.message.tool_calls -and @($final.message.tool_calls).Count -gt 0) {
    throw "Trial $trial failed: repeated tool call"
  }
  if ($final.message.content.Trim() -cne $actual.stamp) {
    throw "Trial $trial failed: tool result was not used exactly"
  }
  "Trial $trial PASS; elapsedMs=$($watch.ElapsedMilliseconds)"
}
```

失败分流：连接错误先检查本地服务；无 tool_calls 则记录模型 ID、原始响应及参数；参数或结果错误按失败登记，不能手工补造调用来通过。调整参数后必须重新计完整 3 次，不自动换模型或升级。

## 后续任务与依赖

每项实现任务执行 Specification → Design → Tasks → TDD Red → Green → Refactor → Review → CI。先将测试失败原因留档，再做最小实现；基础配置和文档无需伪造 Red。下列命令从 D2 建立工程后执行。

| 日 / 任务 | 依赖 | 交付与明确验收标准 |
| --- | --- | --- |
| D1 / T1 规划与探针 | 无 | 四份文档、环境结果、3/3 原生探针、待验证项；人工验收后才开始 D2 |
| D2 / T2 最小工程与 CI | T1 人工验收 | 固定版本 POM、UTF-8、Java 17；`mvn -B -ntp verify` 成功；GitHub Actions 显式使用 Maven 3.8.4 / JDK 17，无 Ollama 也绿；本地与 CI 打印工具版本 |
| D2 / T3 ChatClient 接线验证 | T2 | 非业务只读探针经 ChatClient 和 ToolCallingManager 实际执行并回传；确认自动循环已关闭；真实测试只在 ollama-it profile 启用，失败不得标记集成通过 |
| D3 / T4 堆栈解析 | T2 | A3 所列正常/边界输入先 Red 后 Green；行号、主因、关键帧和 warnings 有固定断言 |
| D3 / T5 JSON 知识库 | T2、T4 输出契约 | 三类知识条目；精确命中、未命中、排序、坏 JSON、重复 ID、非法查询先 Red 后 Green，满足 A4 |
| D4 / T6 有界 Agent 编排 | T3、T4、T5 | 脚本化 ChatModel 驱动实际两工具；循环/批量预算、错参、未知工具、越序、无工具、执行异常、超时取消均先 Red 后 Green，满足 A6 |
| D5 / T7 API 与报告 | T6 | A1/A5：HTTP 边界、完整与 partial、引用伪造、非法报告 JSON、错误映射、请求隔离先 Red 后 Green；MockMvc 不依赖 Ollama |
| D6 / T8 真实模型验收 | T7 | 三类样例各 3 次；每次两工具确实执行、证据可追溯、报告结构合规且在预算内；未知异常与恶意指令各 1 次，核验 partial 和工具白名单；记录全部失败，不挑选成功样本 |
| D7 / T9 Review 与交付 | T8、A1–A7 | A8：逐条验收、完整确定性 CI 绿、真实模型记录、启动/curl/故障说明；未达标项明确列出，不以漂亮输出替代验收 |

测试约定：`mvn -B -ntp verify` 为默认确定性集合（排除 ollama 标签）；`mvn -B -ntp -Pollama-it verify` 额外启用真实模型集合。profile 必须明确标签包含/排除，不能依赖测试文件名偶然被发现。CI 在 push/PR 运行默认集合，禁止启动、安装或调用 Ollama；创建 workflow 后仍须看到真实运行结果才称 CI 通过。

## 风险与下一步

- D2 已验证本地依赖解析、effective POM、固定版本 API 编译及真实 GitHub Actions 构建；未更换版本。后续新增 API 使用仍须按固定版本编译确认。
- 本机 17.0.11 补丁较旧、GBK 与 CI 编码不同：保留 JDK 17，显式 UTF-8 并用 CI 交叉验证；任何升级另行说明依据。
- 原生探针不代表业务链可靠：D2 已验证 Spring AI 最小接线；D6 仍须评估全部业务样本，不通过就修正提示、参数或实现，不跳过门槛。
- 冷启动、内存和并发可能超时：9 秒级探针不是业务性能基准；D6 记录冷/热耗时和失败原因，调整预算须同步规格及测试。
- 知识库有限、模型可能错误推理：证据引用校验与 uncertainties 保留；人工核对诊断，不能声称找到唯一根因。

Day 1 历史交付：只新增 product.md、architecture.md、plan.md、AGENTS.md；当时没有业务代码、构建配置或 CI 文件，单元测试/编译/CI 均未运行。环境检查和原生探针已执行。文档 Review、复验脚本静态语法、本地 Markdown 链接、UTF-8、代码围栏与行尾空白检查通过。用户现已验收 Day 1；当前进度以上方阶段状态及后续执行记录为准。
