# Java Exception Agent：一周执行计划

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

每项实现任务执行 Specification → Design → Tasks → TDD Red → Green → Refactor → Review → CI。先将测试失败原因留档，再做最小实现；基础配置和文档无需伪造 Red。下列命令是后续工程约定，本轮没有 pom.xml，尚不可执行。

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

- 依赖可下载性、固定版本 API 编译尚未验证：D2 先核验 Maven 解析与 effective POM；网络失败报告原始错误，不改版本掩盖。
- 本机 17.0.11 补丁较旧、GBK 与 CI 编码不同：保留 JDK 17，显式 UTF-8 并用 CI 交叉验证；任何升级另行说明依据。
- 原生探针不代表业务链可靠：D2 验证 Spring AI 接线，D6 评估全部样本；不通过就修正提示、参数或实现，不跳过门槛。
- 冷启动、内存和并发可能超时：9 秒级探针不是业务性能基准；D6 记录冷/热耗时和失败原因，调整预算须同步规格及测试。
- 知识库有限、模型可能错误推理：证据引用校验与 uncertainties 保留；人工核对诊断，不能声称找到唯一根因。

本轮只新增 product.md、architecture.md、plan.md、AGENTS.md；没有业务代码、构建配置或 CI 文件。单元测试/编译/CI 均未运行（尚无工程），环境检查和原生探针已执行。文档 Review 已核对验收项、错误映射和任务依赖；复验脚本通过 PowerShell 静态语法解析，本地 Markdown 链接、UTF-8 解码、代码围栏与行尾空白检查通过。下一步为人工验收本轮文档；验收前停止执行后续任务。
