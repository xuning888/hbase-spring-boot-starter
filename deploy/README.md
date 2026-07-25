# HBase 1.2 Standalone (Docker Compose)

为宿主机（arm64 Mac）上运行的 Java 程序提供可通过 ZooKeeper `hbase:2181` 访问的单机 HBase 1.2 服务。

---

## Quickstart（编号操作步骤）

### ① Rosetta 预检（arm64 Mac 必须）

镜像 `harisekhon/hbase:1.2` 仅有 amd64 架构，需要 Rosetta 模拟。

```bash
docker run --rm --platform linux/amd64 alpine uname -m
# 预期输出: x86_64
```

若失败或极慢：Docker Desktop → Settings → General → 启用 "Use Rosetta for x86_64/amd64 emulation on Apple Silicon"，并确保 VM 内存 ≥ 4GB。

### ② 端口占用检查

```bash
lsof -nP -iTCP:2181 -iTCP:16000 -iTCP:16010 -iTCP:16201 -iTCP:16301 -sTCP:LISTEN
# 预期：无输出（所有端口空闲）
```

若有进程占用，用 `lsof -nP -iTCP:<port> -sTCP:LISTEN` 定位并释放。

### ③ 配置 hosts（必须）

HBase 以**主机名**注册到 ZooKeeper，客户端按主机名直连，因此宿主机必须能解析 `hbase`。

```bash
# 判重后追加（幂等）
grep -E '(^|[[:space:]])hbase([[:space:]]|$)' /etc/hosts || \
  echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts
```

### ④ 启动容器

```bash
docker compose -f deploy/docker-compose.yaml up -d
```

### ⑤ 就绪探针（确认 HBase 可用后再启动你的 Java 程序）

```bash
# HBase Master Web UI（需返回 HTTP 200，页面含 hbase 的 RegionServer 记录）
curl -sf http://localhost:16010/master-status

# ZooKeeper 四字命令
echo ruok | nc localhost 2181
# 预期输出: imok

# rootdir 校验（确认 volume 挂载生效）
docker exec hbase hbase org.apache.hadoop.hbase.util.HBaseConfTool hbase.rootdir
# 预期输出含: /hbase-data

# 容器状态
docker compose -f deploy/docker-compose.yaml ps
# 预期: Status = running (healthy) 或 Up
```

首次启动含镜像拉取 + amd64 模拟启动，就绪可能需 3–8 分钟。

### ⑥ hbase shell 冒烟测试

```bash
printf "create 'test_smoke','cf'\nput 'test_smoke','r1','cf:q','v1'\nscan 'test_smoke'\ndisable 'test_smoke'\ndrop 'test_smoke'\n" | docker exec -i hbase hbase shell -n
```

> **注意**：HBase 1.2 的 hbase shell **无 `-e` 选项**，必须用管道 stdin 且 `docker exec` 必须带 `-i`，否则 shell 空跑假成功。amd64 模拟下 JRuby 冷启动每次约 1–3 分钟，勿误判卡死。

### ⑦ 接入你的 Java 程序

在 `application.properties`（或对应配置）中添加：

```properties
xn.hbase.enabled=true
xn.hbase.zookeeper-quorum=hbase
xn.hbase.zookeeper-property-client-port=2181
xn.hbase.auth.enabled=false
xn.hbase.rpc-timeout=60000
xn.hbase.client-retries-number=5
```

> **`xn.hbase.auth.enabled=false` 不可省略！** starter 默认开启 Kerberos，省略后启动即抛 `NullPointerException`（`krb5.startsWith`，XnHBaseAutoConfiguration 第 49 行）。

---

## 运维命令

| 操作 | 命令 | 说明 |
|------|------|------|
| 启动 | `docker compose -f deploy/docker-compose.yaml up -d` | 后台启动，已存在则无操作 |
| 停止 | `docker compose -f deploy/docker-compose.yaml down` | 停止并移除容器，**保留数据** |
| 重置 | `docker compose -f deploy/docker-compose.yaml down -v` | 停止容器 **并清空所有数据** |
| 查看日志 | `docker compose -f deploy/docker-compose.yaml logs -f hbase` | 实时跟踪 |
| 进入容器 | `docker exec -it hbase bash` | 进入容器 shell |

### 持久化语义矩阵

| 操作 | 数据是否保留 |
|------|-------------|
| `docker compose down` | ✅ 保留 |
| Docker Desktop 重启 | ✅ 保留 |
| `docker compose down -v` | ❌ 清空 |
| 删除 named volume `hbase-data` | ❌ 清空 |

数据存储在 named volume `hbase-data`，挂载到容器的 `/hbase-data`（该镜像 standalone 模式的 rootdir）。

---

## 就绪检查命令（汇总）

```bash
# 1. Master Web UI
curl -sf http://localhost:16010/master-status

# 2. ZooKeeper
echo ruok | nc localhost 2181

# 3. rootdir
docker exec hbase hbase org.apache.hadoop.hbase.util.HBaseConfTool hbase.rootdir

# 4. 容器状态
docker compose -f deploy/docker-compose.yaml ps

# 5. 日志检查（预期无持续 ERROR/Fatal）
docker compose -f deploy/docker-compose.yaml logs hbase --tail 50
```

---

## 端口清单

| 端口 | 服务 | 说明 |
|------|------|------|
| 2181 | ZooKeeper | 客户端连接入口 |
| 16000 | HBase Master | Master RPC |
| 16010 | HBase Master Web UI | http://localhost:16010 |
| 16201 | HBase RegionServer | RegionServer RPC（非标准端口） |
| 16301 | HBase RegionServer Info | RegionServer Info 端口 |

---

## 客户端 JVM 要求（关键！）

**你的 Java 程序必须在 JDK 8 下运行。** `hbase-shaded-client:1.2.0` 在 JDK 11+ 必败。

| JDK 版本 | 结果 | 典型错误 |
|----------|------|----------|
| JDK 8 | ✅ 正常 | — |
| JDK 11+ | ❌ 必败 | 各种兼容性问题 |
| JDK 21 | ❌ 必败 | `java.lang.Error` (Bytes UnsafeComparer) 或 `RuntimeException: Unexpected version format: 21.x` |

**运行前设置：**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
```

或在 IDE 中将 Project SDK 设为 1.8（JDK 8）。

---

## 在自己的项目中使用本 starter

### 1. 安装 starter 到本地 Maven 仓库

在仓库根目录执行：

```bash
mvn install -DskipTests
```

这会将 `com.github.xuning888:hbase-spring-boot-starter:1.0-SNAPSHOT` 装入本地仓库。

### 2. 在自己的 pom.xml 中声明依赖

```xml
<dependency>
    <groupId>com.github.xuning888</groupId>
    <artifactId>hbase-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

> **重要**：你还需要**额外声明自己的 `spring-boot-starter`**，因为本 starter 中它为 `provided` scope，不会自动传递。

### 3. 配置属性

参见上方 [⑦ 接入你的 Java 程序](#-接入你的-java-程序)。

### 4. 参考用法

参见 `src/test/java/com/github/xuning888/hbase/HbaseComponentIT.java`（task2 交付）。

---

## 常见故障排查

| 症状 | 可能原因 | 排查/解决 |
|------|----------|-----------|
| `Bind for 0.0.0.0:<port> failed: port is already allocated` | 端口被占用 | `lsof -nP -iTCP:<port> -sTCP:LISTEN` 定位并释放占用进程 |
| `UnknownHostException: hbase` | 宿主机 `/etc/hosts` 未配置 | 执行 `echo '127.0.0.1 hbase' \| sudo tee -a /etc/hosts` |
| master-status 中 RegionServer 显示为 172.x 容器 IP 而非 `hbase` | compose 未使用默认 user-defined 网络 | 检查 `docker-compose.yaml` 是否正确，确保没有自定义网络配置 |
| Java 程序启动后 ~5 次重试报 ZooKeeper ConnectionLoss/超时 | HBase 尚未就绪 | 先跑就绪探针（⑤），确认全部通过后再启动 Java 程序 |
| 模拟环境极慢或崩溃 | Rosetta 未启用或资源不足 | Docker Desktop 启用 Rosetta，VM 内存 ≥ 4GB |
| `NullPointerException` at `krb5.startsWith` | 未设置 `xn.hbase.auth.enabled=false` | 在配置中显式添加 `xn.hbase.auth.enabled=false` |

---

## 清理

```bash
# 停止容器并清空所有数据
docker compose -f deploy/docker-compose.yaml down -v

# 移除 hosts 记录（手动编辑 /etc/hosts，删除含 127.0.0.1 hbase 的行）
sudo sed -i '' '/127\.0\.0\.1[[:space:]]\+hbase/d' /etc/hosts
```
