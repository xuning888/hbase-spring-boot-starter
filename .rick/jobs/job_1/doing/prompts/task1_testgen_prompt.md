# Python 测试脚本生成任务

**YOU MUST declare at the start: "I will use skill:tdd and skill:testing-anti-patterns for test generation."**

## 核心 Skills（必须加载）

在开始任何工作之前，必须读取以下 skill 文件：

- skill:tdd（测试驱动开发）：`/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/jobs/job_1/doing/prompts/skill_tdd_zh.md`
- skill:testing-anti-patterns（测试反模式）：`/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/jobs/job_1/doing/prompts/skill_testing_anti_patterns_zh.md`

你需要根据任务的测试方法生成一个 Python 测试脚本。

## 任务信息

**Task ID**: task1
**Task Name**: 编写 docker-compose 部署文件并启动验证单机 HBase
**Task Goal**: 在 `deploy/docker-compose.yaml` 新建单机版 HBase 的 docker-compose 部署文件，启动容器并验证 HBase 就绪，为宿主机（arm64 Mac）上运行的 Java 程序提供可通过 ZooKeeper `hbase:2181` 访问的 HBase 1.2 服务。

背景约束（均已核实/实证的事实）：
- 客户端为 `hbase-shaded-client:1.2.0`，服务端须版本对齐 → 镜像 `harisekhon/hbase:1.2`（此前已删除的 K8s YAML 同款）。
- 该镜像仅有 amd64 架构，宿主机是 arm64 Mac → compose 必须声明 `platform: linux/amd64`。执行前预检 Rosetta 模拟可用性：`docker run --rm --platform linux/amd64 alpine uname -m` 须快速返回 `x86_64`；若失败或极慢，提示用户在 Docker Desktop 设置启用 Rosetta（并建议 VM 内存 ≥4GB）后再继续。
- HBase 把 Master/RegionServer 位置以**主机名**注册进 ZK，客户端按该主机名直连 → 容器须固定 `hostname: hbase`（docker 会将其写入容器 /etc/hosts 指向容器 eth0 IP，HBase 默认绑 0.0.0.0，宿主机端口映射有效——已推演确认），宿主机 `/etc/hosts` 加 `127.0.0.1 hbase`。
- 该镜像 RegionServer RPC 端口为非标准 16201（info 16301），客户端经 ZK 自动发现，无需客户端侧配置，但宿主机端口映射必须包含它们。
- 数据持久化：named volume 挂 `/hbase-data`（该镜像 standalone 模式的 rootdir），`down -v` 可一键重置。
- 宿主机工具依赖：就绪检查用到 `curl`、`nc`、`lsof`、`docker compose` v2（macOS 自带 + Docker Desktop，已验证存在）。
- sudo 提权策略（改 /etc/hosts 需要）：非交互环境先 `sudo -n true` 探测免密 sudo；失败则**打印确切命令请用户手动执行**（`echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts`），严禁盲等密码；随后用 `grep -E '(^|[[:space:]])hbase([[:space:]]|$)' /etc/hosts` 验证记录已存在再继续。

### 问题记录


## 测试方法

正常路径：前置条件=Docker Desktop 运行中且 Rosetta 预检通过（KR2）；`lsof -nP -iTCP:2181 -iTCP:16000 -iTCP:16010 -iTCP:16201 -iTCP:16301 -sTCP:LISTEN` 无输出（端口全空闲）；`/etc/hosts` 已含 `127.0.0.1 hbase`。输入=`docker compose -f deploy/docker-compose.yaml up -d`。操作=启动后每 10 秒重试一次就绪检查（KR5 全部四项），上限 8 分钟。预期输出=curl 返回 200 且页面含 `hbase` 的 RegionServer 记录；nc 返回 `imok`；rootdir 校验输出含 `/hbase-data`；`docker compose ps` 状态 running；`logs --tail 50` 中 ERROR/Fatal 行数为 0，或仅启动期一次性出现且就绪检查仍通过（不因日志持续刷错而失败）。
边界用例（幂等重复启动）：前置条件=容器已 running 且就绪检查通过。输入=再次执行同一条 `up -d` 命令。预期输出=compose 不重建容器（输出 `Container hbase  Running`，无 Recreated），就绪检查再次通过，`curl -sf http://localhost:16010/master-status` 仍 200。
异常路径（重启恢复 + 持久化）：前置条件=HBase 就绪。操作=写入测试数据：`printf "create 't_persist','cf'\nput 't_persist','rk1','cf:q','v1'\n" | docker exec -i hbase hbase shell -n`（注意：HBase 1.2 的 hbase shell **无 `-e` 选项**，必须用管道 stdin 且 `docker exec` 必须带 `-i`，否则 shell 空跑假成功；amd64 模拟下 JRuby 冷启动每次约 1-3 分钟，勿误判卡死）→ `docker compose -f deploy/docker-compose.yaml down && docker compose -f deploy/docker-compose.yaml up -d`，重新等待就绪后执行 `printf "scan 't_persist'\n" | docker exec -i hbase hbase shell -n | grep rk1`。预期输出=重启后就绪检查通过；grep 命中 `rk1` 行（volume 持久化生效）；最后 `printf "disable 't_persist'\ndrop 't_persist'\n" | docker exec -i hbase hbase shell -n` 清理。端口冲突故障预案（文档化于 README，不强制实操）：若启动报 `Bind for 0.0.0.0:<port> failed: port is already allocated`，用 `lsof -nP -iTCP:<port> -sTCP:LISTEN` 定位并释放占用进程后重试。

## 测试脚本路径

请创建测试脚本到: `/Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/jobs/job_1/doing/tests/task1.py`

## **CRITICAL**: JSON 输出格式要求

测试脚本**必须**输出**恰好一行**有效的 JSON 到 stdout：

### 成功情况
```json
{"pass": true, "errors": []}
```

### 失败情况
```json
{"pass": false, "errors": ["error message 1", "error message 2"]}
```

### JSON 格式规范

1. **`pass`**: 布尔值
   - `true`: 所有测试通过
   - `false`: 至少有一个测试失败

2. **`errors`**: 字符串数组
   - 如果 `pass=true`，必须是空数组 `[]`
   - 如果 `pass=false`，包含所有错误信息

3. **输出规则**:
   - 使用 `print(json.dumps(result))` 输出 JSON
   - **不要**向 stdout 输出其他任何内容
   - 调试信息请输出到 stderr

4. **退出码**:
   - `pass=true` → 退出码 0
   - `pass=false` → 退出码 1

## 测试脚本模板

**请严格遵循以下结构**：

```python
#!/usr/bin/env python3
import json
import sys
import os

def main():
    errors = []

    # Test step 1: 检查文件是否存在
    if not os.path.exists('expected_file.txt'):
        errors.append('expected_file.txt does not exist')

    # Test step 2: 验证文件内容
    try:
        with open('expected_file.txt', 'r') as f:
            content = f.read()
            if 'expected_content' not in content:
                errors.append('expected_file.txt missing expected content')
    except Exception as e:
        errors.append(f'Failed to read expected_file.txt: {str(e)}')

    # Test step 3: 检查其他条件
    # 添加更多测试步骤...

    # 构建结果 JSON
    result = {
        'pass': len(errors) == 0,
        'errors': errors
    }

    # 输出 JSON (CRITICAL: 只有这一行输出到 stdout)
    print(json.dumps(result))

    # 使用合适的退出码
    sys.exit(0 if result['pass'] else 1)

if __name__ == '__main__':
    main()
```

## 测试脚本编写要求

### 1. 实现所有测试步骤
- 根据上面的"测试方法"实现每个测试步骤
- 每个步骤都要有清晰的注释

### 2. 错误收集
- 使用 `errors.append()` 收集所有测试失败
- 不要在第一个错误时就退出
- 收集所有错误后一次性返回

### 3. 异常处理
- 使用 try-except 捕获可能的异常
- 将异常信息添加到 errors 数组
- 示例：`errors.append(f'操作失败: {str(e)}')`

### 4. 路径处理
- **必须使用绝对路径**检查文件
- 使用 `os.path.abspath()` 或 `os.getcwd()` 获取绝对路径
- 示例：`os.path.join(os.getcwd(), 'file.txt')`

### 5. 可执行性
- 添加 shebang: `#!/usr/bin/env python3`
- 脚本应该可以直接运行: `python3 /Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/jobs/job_1/doing/tests/task1.py`

## ✅ DO（必须做）

- ✅ 使用 `print(json.dumps(result))` 输出 JSON
- ✅ 使用 `errors.append()` 收集所有失败
- ✅ `pass=true` 时退出码为 0，`pass=false` 时退出码为 1
- ✅ 使用绝对路径检查文件
- ✅ 使用 try-except 处理异常
- ✅ 实现测试方法中的所有步骤

## ❌ DON'T（禁止做）

- ❌ 向 stdout 输出调试信息（使用 stderr 代替）
- ❌ 输出多个 JSON 对象
- ❌ 返回无效的 JSON 格式
- ❌ 使用相对路径（容易出错）
- ❌ 在第一个错误时就退出（应该收集所有错误）
- ❌ 忘记实现某个测试步骤

## 示例：完整的测试脚本

```python
#!/usr/bin/env python3
import json
import sys
import os

def main():
    errors = []

    # 获取项目根目录（假设测试脚本在 tests/ 目录下）
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    # Test 1: 检查配置文件
    config_file = os.path.join(project_root, 'config.json')
    if not os.path.exists(config_file):
        errors.append('config.json does not exist')
    else:
        try:
            with open(config_file, 'r') as f:
                import json as json_lib
                config = json_lib.load(f)
                if 'api_key' not in config:
                    errors.append('config.json missing api_key field')
        except Exception as e:
            errors.append(f'Failed to parse config.json: {str(e)}')

    # Test 2: 检查日志目录
    log_dir = os.path.join(project_root, 'logs')
    if not os.path.isdir(log_dir):
        errors.append('logs directory does not exist')

    # Test 3: 检查可执行文件
    binary = os.path.join(project_root, 'bin', 'app')
    if not os.path.exists(binary):
        errors.append('bin/app does not exist')
    elif not os.access(binary, os.X_OK):
        errors.append('bin/app is not executable')

    # 构建结果
    result = {
        'pass': len(errors) == 0,
        'errors': errors
    }

    # 输出 JSON
    print(json.dumps(result))

    # 退出
    sys.exit(0 if result['pass'] else 1)

if __name__ == '__main__':
    main()
```

## Cialdini 合规原则

### 权威（Authority）

**YOU MUST generate a failing test first (RED phase). No exceptions.**

测试脚本生成必须覆盖全部验收条件，不得遗漏任何测试步骤。

### 承诺（Commitment）

在开始生成测试脚本前，声明你将使用的 skills：

```
Declare: "I will use skill:tdd and skill:tc for test generation."
```

使用 `skill:tc` 时，必须检查四要素：前置条件 / 输入参数 / 操作序列 / 预期输出。

### 稀缺（Scarcity）

**Before writing any test, verify: you understand the acceptance criteria.**

每个测试步骤都必须对应明确的验收标准，未理解验收条件不得开始编写。

---

## 测试质量自检（强制）

生成测试脚本后，**必须立即运行以下命令**：

```bash
python3 /Users/xuning/IdeaProjects/hbase-spring-boot-starter/.rick/jobs/job_1/doing/tests/task1.py
```

**根据运行结果判断**：

- 输出 `"pass": false` → 符合预期，测试正确覆盖了待实现的功能
- 输出 `"pass": true` → 需要判断原因：
  - ✅ **可接受**：该功能已被前面的 task 顺带实现，测试通过是合理的
  - ❌ **需重写**：功能尚未实现但测试已通过，说明测试逻辑有缺陷（如断言过弱、检查对象错误），**必须重新编写测试脚本**

**你负责判断**，不依赖程序的硬性检查。判断依据：查看当前代码库，确认被测功能是否已存在。

---

## 重要提醒

1. **只生成测试脚本，不要执行任务本身**
2. **严格遵循 JSON 输出格式**，否则测试框架无法解析结果
3. **收集所有错误**，不要在第一个错误时就停止
4. **使用绝对路径**，避免路径相关的错误
5. **测试脚本应该是幂等的**，多次运行应该得到相同结果

现在请生成测试脚本。
