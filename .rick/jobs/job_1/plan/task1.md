# 依赖关系


# 任务名称
编写 docker-compose 部署文件并启动验证单机 HBase

# 任务目标
在 `deploy/docker-compose.yaml` 新建单机版 HBase 的 docker-compose 部署文件，启动容器并验证 HBase 就绪，为宿主机（arm64 Mac）上运行的 Java 程序提供可通过 ZooKeeper `hbase:2181` 访问的 HBase 1.2 服务。

背景约束（均已核实/实证的事实）：
- 客户端为 `hbase-shaded-client:1.2.0`，服务端须版本对齐 → 镜像 `harisekhon/hbase:1.2`（此前已删除的 K8s YAML 同款）。
- 该镜像仅有 amd64 架构，宿主机是 arm64 Mac → compose 必须声明 `platform: linux/amd64`。执行前预检 Rosetta 模拟可用性：`docker run --rm --platform linux/amd64 alpine uname -m` 须快速返回 `x86_64`；若失败或极慢，提示用户在 Docker Desktop 设置启用 Rosetta（并建议 VM 内存 ≥4GB）后再继续。
- HBase 把 Master/RegionServer 位置以**主机名**注册进 ZK，客户端按该主机名直连 → 容器须固定 `hostname: hbase`（docker 会将其写入容器 /etc/hosts 指向容器 eth0 IP，HBase 默认绑 0.0.0.0，宿主机端口映射有效——已推演确认），宿主机 `/etc/hosts` 加 `127.0.0.1 hbase`。
- 该镜像 RegionServer RPC 端口为非标准 16201（info 16301），客户端经 ZK 自动发现，无需客户端侧配置，但宿主机端口映射必须包含它们。
- 数据持久化：named volume 挂 `/hbase-data`（该镜像 standalone 模式的 rootdir），`down -v` 可一键重置。
- 宿主机工具依赖：就绪检查用到 `curl`、`nc`、`lsof`、`docker compose` v2（macOS 自带 + Docker Desktop，已验证存在）。
- sudo 提权策略（改 /etc/hosts 需要）：非交互环境先 `sudo -n true` 探测免密 sudo；失败则**打印确切命令请用户手动执行**（`echo '127.0.0.1 hbase' | sudo tee -a /etc/hosts`），严禁盲等密码；随后用 `grep -E '(^|[[:space:]])hbase([[:space:]]|$)' /etc/hosts` 验证记录已存在再继续。

# 关键结果
1. `deploy/docker-compose.yaml` 存在且包含全部要素：服务名 `hbase`（`docker compose ... logs hbase` 等命令依赖服务名）、镜像 `harisekhon/hbase:1.2`、`platform: linux/amd64`、`hostname: hbase`、`container_name: hbase`、端口映射 2181/16000/16010/16201/16301、named volume `hbase-data` → `/hbase-data`。`docker compose -f deploy/docker-compose.yaml config -q` 校验通过。
2. Rosetta 预检通过：`docker run --rm --platform linux/amd64 alpine uname -m` 输出 `x86_64`。
3. 宿主机 `/etc/hosts` 存在 `127.0.0.1 hbase` 记录（追加前 grep 判重，幂等；sudo 策略按背景约束执行）。
4. `docker compose -f deploy/docker-compose.yaml up -d` 成功，`docker compose -f deploy/docker-compose.yaml ps` 显示容器 running。
5. 就绪验证通过（允许重试，首次含镜像拉取 + 模拟启动，上限 8 分钟）：
   - `curl -sf http://localhost:16010/master-status` 返回 HTTP 200，且页面中包含主机名 `hbase` 的 RegionServer 记录；
   - `echo ruok | nc localhost 2181` 返回 `imok`；
   - rootdir 校验：`docker exec hbase sh -c "grep -A1 hbase.rootdir /hbase/conf/hbase-site.xml"` 输出含 `/hbase-data`（确认 volume 挂载点与镜像实际 rootdir 一致）；
   - `docker compose -f deploy/docker-compose.yaml logs hbase 2>&1 | tail -50` 无持续刷新的 ERROR/Fatal。
6. `deploy/README.md` 存在，面向"我要测试自己的 Java 程序"的使用者，顶部为**编号 quickstart**（① Rosetta 预检 → ② 端口占用检查 → ③ 配置 hosts → ④ `up -d` → ⑤ 就绪探针 → ⑥ hbase shell 冒烟 → ⑦ 接入自己的 Java 程序），并包含以下章节：
   - 运维命令：启动/停止/重置（`up -d` / `down` / `down -v`），附持久化语义矩阵（`down` 与 Docker Desktop 重启均保留数据，`down -v` 才清空）；
   - 就绪检查命令、端口清单与 Web UI（http://localhost:16010）说明；
   - **客户端 JVM 要求（关键）**：你的 Java 程序必须在 **JDK 8** 下运行（hbase-shaded-client 1.2.0 在 JDK 11+ 必败）。翻车签名：JDK 21 报 `java.lang.Error`（Bytes UnsafeComparer）或 `RuntimeException: Unexpected version format: 21.x`；运行前 `export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)`，或将 IDE Project SDK 设为 1.8；
   - **在自己的项目中使用本 starter**：先在仓库根目录执行 `mvn install -DskipTests` 将 `com.github.xuning888:hbase-spring-boot-starter:1.0-SNAPSHOT` 装入本地仓库；自己的 pom 中声明该依赖，并**额外声明自己的 `spring-boot-starter`**（本 starter 中它为 provided scope）；用法参考 `src/test/java/com/github/xuning888/hbase/HbaseComponentIT.java`（task2 交付）；
   - 宿主机接入配置样例（见下，**`xn.hbase.auth.enabled=false` 不可省略**：starter 默认开启 Kerberos，省略后启动即抛 `NullPointerException`（`krb5.startsWith`，XnHbaseAutoConfiguration 第 49 行））；
   - 常见故障排查表：端口冲突（`Bind for 0.0.0.0:<port> failed` → `lsof -nP -iTCP:<port> -sTCP:LISTEN` 定位释放）；hosts 未配置 → `UnknownHostException: hbase`；master-status 中 RS 显示为 172.x 容器 IP 而非主机名 hbase → 检查 compose 使用默认 user-defined 网络；未等就绪就启动 Java 程序 → 约 5 次重试后 ZooKeeper ConnectionLoss/超时（先跑就绪探针再启动程序）；模拟环境极慢/崩溃 → Docker Desktop 启用 Rosetta、VM 内存 ≥4GB；
   - 清理说明：`down -v` 清空数据；移除 hosts 记录（`127.0.0.1 hbase` 行）。

   接入配置样例（与 task2 集成测试属性保持一致）：
   ```properties
   xn.hbase.enabled=true
   xn.hbase.zookeeper-quorum=hbase
   xn.hbase.zookeeper-property-client-port=2181
   xn.hbase.auth.enabled=false
   xn.hbase.rpc-timeout=60000
   xn.hbase.client-retries-number=5
   ```

# 测试方法
1. 正常路径：前置条件=Docker Desktop 运行中且 Rosetta 预检通过（KR2）；`lsof -nP -iTCP:2181 -iTCP:16000 -iTCP:16010 -iTCP:16201 -iTCP:16301 -sTCP:LISTEN` 无输出（端口全空闲）；`/etc/hosts` 已含 `127.0.0.1 hbase`。输入=`docker compose -f deploy/docker-compose.yaml up -d`。操作=启动后每 10 秒重试一次就绪检查（KR5 全部四项），上限 8 分钟。预期输出=curl 返回 200 且页面含 `hbase` 的 RegionServer 记录；nc 返回 `imok`；rootdir 校验输出含 `/hbase-data`；`docker compose ps` 状态 running；`logs --tail 50` 中 ERROR/Fatal 行数为 0，或仅启动期一次性出现且就绪检查仍通过（不因日志持续刷错而失败）。
2. 边界用例（幂等重复启动）：前置条件=容器已 running 且就绪检查通过。输入=再次执行同一条 `up -d` 命令。预期输出=compose 不重建容器（输出 `Container hbase  Running`，无 Recreated），就绪检查再次通过，`curl -sf http://localhost:16010/master-status` 仍 200。
3. 异常路径（重启恢复 + 持久化）：前置条件=HBase 就绪。操作=写入测试数据：`printf "create 't_persist','cf'\nput 't_persist','rk1','cf:q','v1'\n" | docker exec -i hbase hbase shell -n`（注意：HBase 1.2 的 hbase shell **无 `-e` 选项**，必须用管道 stdin 且 `docker exec` 必须带 `-i`，否则 shell 空跑假成功；amd64 模拟下 JRuby 冷启动每次约 1-3 分钟，勿误判卡死）→ `docker compose -f deploy/docker-compose.yaml down && docker compose -f deploy/docker-compose.yaml up -d`，重新等待就绪后执行 `printf "scan 't_persist'\n" | docker exec -i hbase hbase shell -n | grep rk1`。预期输出=重启后就绪检查通过；grep 命中 `rk1` 行（volume 持久化生效）；最后 `printf "disable 't_persist'\ndrop 't_persist'\n" | docker exec -i hbase hbase shell -n` 清理。端口冲突故障预案（文档化于 README，不强制实操）：若启动报 `Bind for 0.0.0.0:<port> failed: port is already allocated`，用 `lsof -nP -iTCP:<port> -sTCP:LISTEN` 定位并释放占用进程后重试。
