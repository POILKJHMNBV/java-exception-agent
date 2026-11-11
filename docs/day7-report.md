# Day 7 / T9 Review 与交付记录

本地交付日期：2026-11-10（Asia/Shanghai）。用户明确表示“Day6已验收通过，现在进行Day7”，初次仅授权本地阶段工作。用户于 2026-11-11 明确表示“Day7我验收通过，提交、推送，远程 CI 本次变更”，已授权本次提交、推送与远程 CI。阶段状态仅记录在 [plan.md](plan.md)。

## 任务、完成内容与文件

| 任务 / 验收 | 完成内容 | 文件 |
| --- | --- | --- |
| T9.1 / A1–A8 | 对照产品逐条审查实现、测试和历史证据；整理核心逻辑 Red/Green 索引，区分确定性、真实模型、历史 CI 与本阶段未运行项 | [验收矩阵](acceptance.md) |
| T9.2 / A8 | 固定环境、构建、前台启动、五类 curl 样例、响应解读、错误码、故障演示与复验命令、已知局限 | [README](../README.md)；[npe](../examples/npe.json)、[iae](../examples/iae.json)、[nfe](../examples/nfe.json)、[unknown](../examples/unknown.json)、[unrecognized](../examples/unrecognized.json) |
| T9.2 / A1、A5 | 打包应用真实 HTTP + 本机 qwen3.5:9b 成功演示；错误输入与连接不可达验证；响应关联 ID、输入行、知识内容和引用核对 | [HTTP 批次 01](evidence/day7/http-smoke-01.json) |
| T9.3 / A8 | 默认 verify、Java 注释/显式类型、文档/证据检查；阶段记录与交付报告 | [plan.md](plan.md)、本文件 |

生产与测试 Java、POM、配置、知识库、CI 工作流均未修改；没有需求/架构/版本调整。开始时工作树干净，基线 `0a624171cf45c43161789c755e8a1be50bf9a87d`。

## Red / Green / 最终命令与结果

本阶段没有新增核心算法，无需新业务 Red/Green；既有全部历史 Red/Green、失败原因与命令在验收矩阵和 Day 2–6 报告中可追溯，未修改断言、跳过测试或删除失败事实。

| 命令 / 检查 | 实际结果 |
| --- | --- |
| `java -version`、`mvn -version` | Oracle JDK 17.0.11、Maven 3.8.4；固定 Boot 4.1.1、Spring AI 2.0.1 未变 |
| `mvn -B -ntp verify` | 2026-11-10 22:14:19 完成，188/188，0 失败/错误/跳过，BUILD SUCCESS，可执行 JAR 打包成功；真实模型标签未运行 |
| `ollama list`、`curl.exe --noproxy "*" --silent --show-error --max-time 5 http://localhost:11434/api/version` | 已有 qwen3.5:9b，ID 前缀 6488c96fa5fa；服务 0.35.0。没有下载、替换模型或停止现有 Ollama |
| README 启动与 curl 命令；批量核对使用临时 `& ./target/day7-http-smoke.ps1` | 22:17:59–22:18:17，9/9 场景符合预期，详见下表。临时脚本和完整过程输出仅在 target，不归档 |
| JVM 参数命令草稿复核：`java -Dfile.encoding=UTF-8 -version` | PowerShell 误解析，报 `.encoding=UTF-8` 主类不存在；这是文档命令失败，不是业务 Red。改为 `java "-Dfile.encoding=UTF-8" -version` 后正常输出 17.0.11 |
| 修正后的 README 故障启动完整命令与 curl | 22:22–22:23 前台命令实际启动 18081；使用未监听的本机 11435 模型地址，真实响应 503 / MODEL_UNAVAILABLE。随后显式停止本次启动的进程；进程结束码 1 来自停止动作，不是测试失败 |
| `jshell scripts/review-java.jsh` | 33 个 Java 文件，Missing Javadoc=0；本机 Windows Preferences 注册表写入警告仍存在，未影响 AST 结果，未调整权限 |
| `rg -n '\bvar\b' src -g '*.java'` | 零命中（rg 的退出码 1 表示未匹配，不是检查失败） |
| 历史证据 SHA-256 与 CI 代码关联 | Day 6 五批证据及四个最终源文件均匹配 manifest；`git diff 70a0b4b HEAD -- src pom.xml .github/workflows/ci.yml` 为空，工作树亦未修改这些文件 |
| 临时 `& ./target/day7-review.ps1`、`git diff --check` | 全部 Markdown 本地链接、UTF-8、围栏和行尾空白检查通过；20 份 JSON 均可解析，证据均按阶段且没有过程日志。五个历史批次成败数量/哈希、四个最终源文件哈希、最终 12 份结构化调用/宿主状态/回传/报告序列均通过；本次 HTTP 原始文件及 JAR 哈希与归档一致。Git 仅提示 LF/CRLF 转换，无 diff 空白错误 |

初次交付的完整 verify 之后仅添加或完善文档、合成 JSON 请求样例与 HTTP 响应证据，没有编译源或构建配置变更。当时没有重复构建；验收后按提交前规则另行复验，见下方记录。默认集合的 188 项明细见验收矩阵。

## 打包应用 HTTP 复验

所有成功请求使用生产配置及真实 Ollama，启动命令为 README 所列 JAR 命令；无模型替身。curl 使用 `--data-binary @文件`，客户端上限 130 秒，应用硬预算保持不变。异常样本文件是合成输入；服务没有新增日志或测试观测端点。

| 样本 / 故障 | HTTP / 实际结果 | 总耗时 ms |
| --- | --- | --- |
| NPE | 200 / complete，java.lang.NullPointerException，npe-001 | 5241 |
| IAE | 200 / complete，java.lang.IllegalArgumentException，iae-001 | 2364 |
| NFE | 200 / complete，java.lang.NumberFormatException，nfe-001 | 2341 |
| 未知 WidgetException | 200 / partial，保留类型，知识为空 | 1956 |
| 不可识别中文文本 | 200 / partial，类型 null，证据与原因均空 | 1237 |
| `{}` 请求 | 400 / INVALID_REQUEST | 14 |
| text/plain | 415 / UNSUPPORTED_MEDIA_TYPE | 12 |
| 16,001 码点 | 413 / INPUT_TOO_LARGE | 14 |
| 模型地址指向未监听本机端口 | 503 / MODEL_UNAVAILABLE | 167 |

9 个响应均核对体/头 diagnosisId 一致；成功报告七字段齐全、不确定性非空，原因/建议引用存在于本次目录，堆栈文本等于输入行，知识证据全文等于本地条目。批次记录保留全部九次原始 JSON 响应及时间、HTTP 状态、JAR SHA-256，不包含模型思考、提示词或过程控制台。没有删去失败后挑选成功；本批九次均通过。

本次 HTTP 演示没有逐工具结构化观测，不能替代 A2；A2 仍使用 Day 6 原始闭环批次。未为演示停止真实 Ollama；连接失败通过独立应用实例与空闲本机端口复现。全部本次应用进程已退出，未占用 8080/18081。

## Review、风险与 CI

- 单 Agent 自查，未使用多 Agent 或声称独立评审。复核真实回调、执行前白名单/参数/状态、4/5 次数、单调总 deadline、传输零重试、真实取消、有界执行器、残留容量及跨请求隔离；既有故障测试全部通过。
- 输入有界读取、UTF-8/码点/换行、严格 JSON、报告证据组装、partial 与错误脱敏均已检查。解析器无跨请求可变状态，知识库不可变且关闭加载资源；没有任意路径/URL/命令工具入口。
- 语义 Review 有保留：本次 NPE 的第二原因把 name 称为调用参数，建议也按参数路径追溯，尚无源码证实；IAE 再次列出 null 备选；NFE 泛化罗列范围溢出，未聚焦 `12x`。本次没有声称解决 Day 6 语义风险；结构与引用有效不等于推理正确。
- 请求总预算从诊断服务入口计时，不覆盖上传正文；HTTP 取消不保证 Ollama 立即停止推理，完整模型响应缓冲在内存。本机短合成样例不能代表长输入/并发性能或覆盖所有注入形式。README 明确本机使用边界与人工审核要求。
- Day 6 历史 [CI 42829839907](https://github.com/POILKJHMNBV/java-exception-agent/actions/runs/42829839907) 为 completed / success，对应 `70a0b4bb328f4ebaeac87fdc2ebf74621b6eb55b`；当前业务源码/测试/构建/工作流一致。初次交付时 Day 7 远程 CI 未运行，提交与推送未执行；验收后本次操作见下方记录，历史 CI 不代替本次 CI。完整真实 profile 本阶段未重跑，已有哈希一致的验收证据并补充了真实 HTTP 复验。
- 证据目录按 dayN 归档，仅新增一份必要的结构化 HTTP 报告；旧批次内容未改，构建/测试/启动日志及临时脚本只在 target。无产品、架构、固定版本或硬预算偏差。

## 验收后提交与 CI 记录（2026-11-11）

- 用户明确验收 Day 7 并授权本次提交、推送及远程 CI；没有新增实现范围或更改已验收需求。
- 提交前 `mvn -B -ntp verify` 于 13:19:17（Asia/Shanghai）通过：188/188，0 失败/错误/跳过，JAR 打包成功。过程输出仅在 target；真实模型未重复运行。
- 提交前 Review 再次核对文档、合成样例、历史模型与 HTTP 证据的完整性及哈希；生产源码、测试、POM 和工作流未变。本次无新业务 Red/Green。
- 初次沙箱内 Git SSH 信号管道和 curl TLS 凭据调用失败；在授权范围内使用沙箱外调用后，远程核对成功。未安装额外 CLI、未更改 Git/网络配置。远程 master 与本地基线同为 `0a624171cf45c43161789c755e8a1be50bf9a87d`。
- 提交、推送和本次 CI 待执行后记录真实提交及运行结果，不提前标记通过。
