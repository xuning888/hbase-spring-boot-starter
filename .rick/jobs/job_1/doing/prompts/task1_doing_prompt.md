# Rick 项目执行阶段

## 角色定义

你是一个资深的软件工程师。你的任务是执行规划好的任务，完成具体的编码工作。

---

## 先验知识（执行前必读）

## 可用的项目 Loops

（暂无项目 Loop 记录）


## 可用的项目 Skills

（暂无项目 Skill 记录）


---

## Job 上下文

# Debug Log - job_1/task1

## Summary

Created docker-compose deployment for standalone HBase 1.2 with data persistence.

## Key Fixes

### Fix 1: ZooKeeper Data Persistence
- **Problem**: After `docker compose down && up -d`, user tables were lost despite being on the named volume filesystem. `hbase:meta` table lost entries for user tables.
- **Root Cause**: ZooKeeper stores ephemeral data in `/tmp/hbase-root/` inside the container. After container recreation, ZK starts fresh with no state. HBase Master's SplitLogManager fails to properly replay WALs because it can't coordinate with stale RegionServer state in ZK, resulting in "Returning success without actually splitting" warnings.
- **Fix**: Added a second named volume `zk-data` mounted at `/tmp/hbase-root` in docker-compose.yaml to persist ZooKeeper state across container recreation.
- **Verification**: Created table, put data, ran `docker compose down && up -d`, scanned table — data persisted successfully.

### Fix 2: Test Idempotency
- **Problem**: Test 10 (write test data) failed on re-runs because `t_persist` table persisted from previous run (the persistence feature we wanted!).
- **Fix**: Split Test 10 into two phases: (1) cleanup (disable/drop, ignoring errors), (2) create/put (checking return code).

## Key Results Status

| KR | Status | Notes |
|----|--------|-------|
| KR1: docker-compose.yaml with all required elements | ✅ | Contains all ports, platform, hostname, volumes |
| KR2: Rosetta precheck | ✅ | Verified x86_64 |
| KR3: /etc/hosts entry | ⚠️ | Requires user to run: `echo '127.0.0.1 hbase' \| sudo tee -a /etc/hosts` |
| KR4: docker compose up -d success | ✅ | Container running |
| KR5: Readiness verification (curl, nc, rootdir, ps, logs) | ✅ | All checks pass |
| KR6: deploy/README.md exists | ✅ | All required sections present |

## Remaining Manual Step

The `/etc/hosts` entry requires sudo. Non-interactive environment cannot prompt for password. User must manually run:
```bash
echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts
```


---

## 任务信息

**任务 ID**: task1
**任务名称**: 编写 docker-compose 部署文件并启动验证单机 HBase

### 任务目标
在 `deploy/docker-compose.yaml` 新建单机版 HBase 的 docker-compose 部署文件，启动容器并验证 HBase 就绪，为宿主机（arm64 Mac）上运行的 Java 程序提供可通过 ZooKeeper `hbase:2181` 访问的 HBase 1.2 服务。

背景约束（均已核实/实证的事实）：
- 客户端为 `hbase-shaded-client:1.2.0`，服务端须版本对齐 → 镜像 `harisekhon/hbase:1.2`（此前已删除的 K8s YAML 同款）。
- 该镜像仅有 amd64 架构，宿主机是 arm64 Mac → compose 必须声明 `platform: linux/amd64`。执行前预检 Rosetta 模拟可用性：`docker run --rm --platform linux/amd64 alpine uname -m` 须快速返回 `x86_64`；若失败或极慢，提示用户在 Docker Desktop 设置启用 Rosetta（并建议 VM 内存 ≥4GB）后再继续。
- HBase 把 Master/RegionServer 位置以**主机名**注册进 ZK，客户端按该主机名直连 → 容器须固定 `hostname: hbase`（docker 会将其写入容器 /etc/hosts 指向容器 eth0 IP，HBase 默认绑 0.0.0.0，宿主机端口映射有效——已推演确认），宿主机 `/etc/hosts` 加 `127.0.0.1 hbase`。
- 该镜像 RegionServer RPC 端口为非标准 16201（info 16301），客户端经 ZK 自动发现，无需客户端侧配置，但宿主机端口映射必须包含它们。
- 数据持久化：named volume 挂 `/hbase-data`（该镜像 standalone 模式的 rootdir），`down -v` 可一键重置。
- 宿主机工具依赖：就绪检查用到 `curl`、`nc`、`lsof`、`docker compose` v2（macOS 自带 + Docker Desktop，已验证存在）。
- sudo 提权策略（改 /etc/hosts 需要）：非交互环境先 `sudo -n true` 探测免密 sudo；失败则**打印确切命令请用户手动执行**（`echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts`），严禁盲等密码；随后用 `grep -E '(^|[[:space:]])hbase([[:space:]]|$)' /etc/hosts` 验证记录已存在再继续。

### 关键结果
1. `deploy/docker-compose.yaml` 存在且包含全部要素：服务名 `hbase`（`docker compose ... logs hbase` 等命令依赖服务名）、镜像 `harisekhon/hbase:1.2`、`platform: linux/amd64`、`hostname: hbase`、`container_name: hbase`、端口映射 2181/16000/16010/16201/16301、named volume `hbase-data` → `/hbase-data`。`docker compose -f deploy/docker-compose.yaml config -q` 校验通过。
2. Rosetta 预检通过：`docker run --rm --platform linux/amd64 alpine uname -m` 输出 `x86_64`。
3. 宿主机 `/etc/hosts` 存在 `127.0.0.1 hbase` 记录（追加前 grep 判重，幂等；sudo 策略按背景约束执行）。
4. `docker compose -f deploy/docker-compose.yaml up -d` 成功，`docker compose -f deploy/docker-compose.yaml ps` 显示容器 running。
5. 就绪验证通过（允许重试，首次含镜像拉取 + 模拟启动，上限 8 分钟）：
6. `curl -sf http://localhost:16010/master-status` 返回 HTTP 200，且页面中包含主机名 `hbase` 的 RegionServer 记录；
7. `echo ruok | nc localhost 2181` 返回 `imok`；
8. rootdir 校验：`docker exec hbase sh -c "grep -A1 hbase.rootdir /hbase/conf/hbase-site.xml"` 输出含 `/hbase-data`（确认 volume 挂载点与镜像实际 rootdir 一致）；
9. `docker compose -f deploy/docker-compose.yaml logs hbase 2>&1 | tail -50` 无持续刷新的 ERROR/Fatal。
10. `deploy/README.md` 存在，面向"我要测试自己的 Java 程序"的使用者，顶部为**编号 quickstart**（① Rosetta 预检 → ② 端口占用检查 → ③ 配置 hosts → ④ `up -d` → ⑤ 就绪探针 → ⑥ hbase shell 冒烟 → ⑦ 接入自己的 Java 程序），并包含以下章节：
11. 运维命令：启动/停止/重置（`up -d` / `down` / `down -v`），附持久化语义矩阵（`down` 与 Docker Desktop 重启均保留数据，`down -v` 才清空）；
12. 就绪检查命令、端口清单与 Web UI（http://localhost:16010）说明；
13. **客户端 JVM 要求（关键）**：你的 Java 程序必须在 **JDK 8** 下运行（hbase-shaded-client 1.2.0 在 JDK 11+ 必败）。翻车签名：JDK 21 报 `java.lang.Error`（Bytes UnsafeComparer）或 `RuntimeException: Unexpected version format: 21.x`；运行前 `export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)`，或将 IDE Project SDK 设为 1.8；
14. **在自己的项目中使用本 starter**：先在仓库根目录执行 `mvn install -DskipTests` 将 `com.github.xuning888:hbase-spring-boot-starter:1.0-SNAPSHOT` 装入本地仓库；自己的 pom 中声明该依赖，并**额外声明自己的 `spring-boot-starter`**（本 starter 中它为 provided scope）；用法参考 `src/test/java/com/github/xuning888/hbase/HbaseComponentIT.java`（task2 交付）；
15. 宿主机接入配置样例（见下，**`xn.hbase.auth.enabled=false` 不可省略**：starter 默认开启 Kerberos，省略后启动即抛 `NullPointerException`（`krb5.startsWith`，XnHbaseAutoConfiguration 第 49 行））；
16. 常见故障排查表：端口冲突（`Bind for 0.0.0.0:<port> failed` → `lsof -nP -iTCP:<port> -sTCP:LISTEN` 定位释放）；hosts 未配置 → `UnknownHostException: hbase`；master-status 中 RS 显示为 172.x 容器 IP 而非主机名 hbase → 检查 compose 使用默认 user-defined 网络；未等就绪就启动 Java 程序 → 约 5 次重试后 ZooKeeper ConnectionLoss/超时（先跑就绪探针再启动程序）；模拟环境极慢/崩溃 → Docker Desktop 启用 Rosetta、VM 内存 ≥4GB；
17. 清理说明：`down -v` 清空数据；移除 hosts 记录（`127.0.0.1 hbase` 行）。


### 测试方法
正常路径：前置条件=Docker Desktop 运行中且 Rosetta 预检通过（KR2）；`lsof -nP -iTCP:2181 -iTCP:16000 -iTCP:16010 -iTCP:16201 -iTCP:16301 -sTCP:LISTEN` 无输出（端口全空闲）；`/etc/hosts` 已含 `127.0.0.1 hbase`。输入=`docker compose -f deploy/docker-compose.yaml up -d`。操作=启动后每 10 秒重试一次就绪检查（KR5 全部四项），上限 8 分钟。预期输出=curl 返回 200 且页面含 `hbase` 的 RegionServer 记录；nc 返回 `imok`；rootdir 校验输出含 `/hbase-data`；`docker compose ps` 状态 running；`logs --tail 50` 中 ERROR/Fatal 行数为 0，或仅启动期一次性出现且就绪检查仍通过（不因日志持续刷错而失败）。
边界用例（幂等重复启动）：前置条件=容器已 running 且就绪检查通过。输入=再次执行同一条 `up -d` 命令。预期输出=compose 不重建容器（输出 `Container hbase  Running`，无 Recreated），就绪检查再次通过，`curl -sf http://localhost:16010/master-status` 仍 200。
异常路径（重启恢复 + 持久化）：前置条件=HBase 就绪。操作=写入测试数据：`printf "create 't_persist','cf'\nput 't_persist','rk1','cf:q','v1'\n" | docker exec -i hbase hbase shell -n`（注意：HBase 1.2 的 hbase shell **无 `-e` 选项**，必须用管道 stdin 且 `docker exec` 必须带 `-i`，否则 shell 空跑假成功；amd64 模拟下 JRuby 冷启动每次约 1-3 分钟，勿误判卡死）→ `docker compose -f deploy/docker-compose.yaml down && docker compose -f deploy/docker-compose.yaml up -d`，重新等待就绪后执行 `printf "scan 't_persist'\n" | docker exec -i hbase hbase shell -n | grep rk1`。预期输出=重启后就绪检查通过；grep 命中 `rk1` 行（volume 持久化生效）；最后 `printf "disable 't_persist'\ndrop 't_persist'\n" | docker exec -i hbase hbase shell -n` 清理。端口冲突故障预案（文档化于 README，不强制实操）：若启动报 `Bind for 0.0.0.0:<port> failed: port is already allocated`，用 `lsof -nP -iTCP:<port> -sTCP:LISTEN` 定位并释放占用进程后重试。




---

**你需要一步步执行以下操作，不可跳过任何步骤。**



## 第一步：执行 Doing Loop

# Doing Loop

> ⚠️ 以下是默认 loop 的执行步骤，也是 gen-loop 需要参考的 skill 模板！！

---

## Step 0：Domain 搜索 + Loop 匹配

**必须依次完成以下两项，再进入 Step 1：**

### 0.1 搜索 Domain（强制）

根据澄清的需求，读取 `/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/domain` 下的相关文件，获取足够的事实信息（环境配置、已知问题、接口约束、构建命令等），建立解决问题的基本视角。

- 由 AI 自行判断读取哪些文件，但**必须完成搜索动作**后再继续
- 遇到任何问题（编译报错 / 测试失败 / 行为异常），**必须优先搜索 `/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/domain/bugs.md` 和 `/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/domain/`**，再做其他尝试

### 0.2 匹配 Loop

在 Domain 搜索完毕后，读取 `loops_context`，按 trigger 字段匹配当前任务/需求：

- **有匹配** → 读取对应 Loop 文件，按其定义步骤执行（不再执行以下 Step 1–5）
- **无匹配** → 按以下 Step 1–5 执行默认 Loop

---

## Step 1：Main Agent 确认全局目标

确认以下内容全部清晰后才继续：

- task.md 中 `# 任务目标` 和 `# 关键结果` 已理解
- 成功标准已明确：测试脚本全通过 + check pass + 所有 Key Results 达成

---

## Step 2：Main Agent 读取上下文（压缩策略）

从 `doing/debug/` 目录读取已有信息，按以下方式压缩后传递给 Sub Agent：

- **bug\*.md** → 从每个文件的 frontmatter `summary` 字段提取摘要，避免重复踩坑
- **跨轮核心事实** → 任务目标 + Key Results 达成状态 + debug/ 摘要 + 当前迭代编号 N

---

## Step 3：启动 Sub Agent 执行工作流

**每轮迭代由 Main Agent 启动一个独立 Sub Agent，携带 Step 2 的上下文，执行完整工作流后返回产出摘要。**

```
[Main Agent]
   │
   ├─ SPAWN Sub Agent（携带：任务目标 + debug/摘要 + 迭代编号 N）
   │     │
   │     │  Sub Agent 执行：
   │     │  [ANALYZE] → [RED] → [GREEN] → [REFACTOR] → [COMMIT]
   │     │                 ↑        │
   │     │                 └──[DEBUG]┘
   │     │
   │     └─ Sub Agent 完成，输出产出摘要
   │
   └─ Main Agent 执行 Step 4 产出评估
```

### Sub Agent：ANALYZE（理解需求）
1. 声明：`"I will use skill:sense."`，按 S→E→N 分析（Symptoms / Evidence / Next）
2. 读取 debug/ 摘要，避免重复踩坑

### Sub Agent：RED（先写失败测试）
1. 声明：`"I will use skill:tdd for implementation."`
2. 针对 `# 测试方法` 中每个场景编写测试
3. 运行测试，**必须确认 FAIL**（证明测试有效，进入 GREEN 的前提）

### Sub Agent：GREEN（最小实现）
1. 编写让测试通过的最小实现代码（不超出 task scope）
2. 通过 → REFACTOR；失败 → DEBUG

### Sub Agent：DEBUG（遇红强制触发）

触发条件（任意一条）：测试 FAIL / 编译报错 / 行为与预期不符

1. **优先搜索 `/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/domain/bugs.md` 和 `/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/domain/`**，查看是否有精确解决方案
   - 有匹配 → 直接应用，记录引用来源
   - 无匹配 → 继续下方流程
2. 声明：`"I will use skill:debug-skill."`，加载 skill 文件：`/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/jobs/job_1/doing/prompts/skill_debug_skill.md`
3. 在 `doing/debug/` 下创建 `bug{N}-{描述}.md`，按 Phase 1-6 执行
4. Phase 4 上限 3 次，达上限后输出当前状态并升级人工协作
5. 修复后回到 GREEN

### Sub Agent：REFACTOR（代码改善）
1. 测试全绿后改善代码质量（命名、结构、去重）
2. 运行全量测试确认无回归；回归失败 → DEBUG

### Sub Agent：COMMIT（收尾提交）
1. `git add` + `git commit`（commit message 含 task ID）
2. 运行 check 命令（使用 prompt 上下文中的 rick_bin_path 和 job_id）：
   - doing 阶段：`<rick_bin_path> tools doing_check <job_id>`
   - easy 阶段：`<rick_bin_path> tools easy_check <job_id>`
3. check 失败 → 修复后重新运行，循环直到 pass
4. **Sub Agent 完成**：输出本轮产出摘要（完成了哪些 KR、遗留了哪些问题），通知 Main Agent 执行 Step 4

---

## Step 4：Main Agent 产出评估

Sub Agent 完成后，Main Agent 逐项检查：

| 检查项 | 判断方法 |
|--------|----------|
| check pass | 读取 doing_check / easy_check 输出，确认 ✅ |
| 测试全通过 | 确认测试脚本无 FAIL 输出 |
| Key Results 达成 | 逐条比对 task.md `# 关键结果` |

- **全部通过** → 进入 Step 5
- **存在失败** → 将失败原因附加到上下文，返回 Step 3 启动下一轮迭代

---

## Step 5：Main Agent 确认停止标准

**成功退出**：check pass + 测试全通过 + 所有 Key Results 达成

**优雅退出**（任意一条触发）：
- 迭代次数达上限（默认 **3 轮**）
- 连续 2 轮产出相同错误（判断无法自动收敛）
- 人类明确要求停止

**退出时**：Main Agent 输出 Loop 执行摘要（完成了哪些 KR、遗留了哪些问题），等待人类决策。





---

## 第二步：格式检查

`rick tools doing_check job_1`

check pass 后才算完成。




## Test Execution Feedback

**Previous test execution encountered errors. You may need to fix the test script.**

```
[TEST] 7. PASS: HBase is ready
[TEST] 8. Idempotent restart: running up -d again...
[TEST] 8. PASS: Idempotent restart - no recreate
[TEST] 9. Checking readiness after idempotent restart...
[TEST] 9. PASS: Readiness still OK after idempotent restart
[TEST] 10. Writing test data for persistence check...
[TEST] 10. PASS: Test data written (t_persist table created)
[TEST] 11. Restart cycle: docker compose down && up -d...
[TEST] 11. PASS: Restart cycle completed
[TEST] 12. Waiting for readiness after restart...
[DEBUG] Readiness check failed at 0s, retrying in 10s (max 480s remaining). Failures: ['curl master-status failed (rc=56): ', 'nc ZK ruok failed: stdout=, stderr=']
[TEST] 12. PASS: HBase ready after restart
[TEST] 13. Verifying data persistence...
[TEST] 13. PASS: Data persisted (rk1 found after restart)
[TEST] 14. Cleanup: dropping test table...
[TEST] 14. PASS: Cleanup completed

=== Attempt 4 ===
test did not pass: /etc/hosts does not contain "hbase" entry. Run: echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts

Full test output:
{"pass": false, "errors": ["/etc/hosts does not contain \"hbase\" entry. Run: echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts"]}

STDERR:
[DEBUG] Project root: /Users/xuning/IdeaProjects/hbase-spring-boot-starter
[DEBUG] Compose file: /Users/xuning/IdeaProjects/hbase-spring-boot-starter/deploy/docker-compose.yaml
[TEST] 1. Checking docker-compose.yaml exists...
[TEST] 1. PASS: /Users/xuning/IdeaProjects/hbase-spring-boot-starter/deploy/docker-compose.yaml exists
[TEST] 2. Checking Docker is running...
[TEST] 2. PASS: Docker 28.5.2 is running
[TEST] 3. Checking Rosetta emulation (amd64 on arm64)...
[TEST] 3. PASS: Rosetta emulation works (x86_64 confirmed)
[TEST] 4. Checking required ports are free...
[TEST] 4. PASS: HBase container already running (ports in use by design)
[TEST] 5. Checking /etc/hosts for hbase entry...
[TEST] 6. Starting HBase with docker compose up -d...
[TEST] 6. PASS: docker compose up -d succeeded
[TEST] 7. Performing readiness check (retry up to 480s)...
[TEST] 7. PASS: HBase is ready
[TEST] 8. Idempotent restart: running up -d again...
[TEST] 8. PASS: Idempotent restart - no recreate
[TEST] 9. Checking readiness after idempotent restart...
[TEST] 9. PASS: Readiness still OK after idempotent restart
[TEST] 10. Writing test data for persistence check...
[TEST] 10. PASS: Test data written (t_persist table created)
[TEST] 11. Restart cycle: docker compose down && up -d...
[TEST] 11. PASS: Restart cycle completed
[TEST] 12. Waiting for readiness after restart...
[DEBUG] Readiness check failed at 0s, retrying in 10s (max 480s remaining). Failures: ['curl master-status failed (rc=56): ', 'nc ZK ruok failed: stdout=, stderr=']
[TEST] 12. PASS: HBase ready after restart
[TEST] 13. Verifying data persistence...
[TEST] 13. PASS: Data persisted (rk1 found after restart)
[TEST] 14. Cleanup: dropping test table...
[TEST] 14. PASS: Cleanup completed


```
