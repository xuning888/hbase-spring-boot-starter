# 依赖关系
task1

# 任务名称
编写 starter 集成测试并以 TDD 修复共享连接被关闭的缺陷

# 任务目标
为 hbase-spring-boot-starter 编写基于真实 docker HBase（task1 产物）的 JUnit4 集成测试，完整模拟用户 Java 程序的真实使用路径：Spring 自动装配 `XnHbaseAutoConfiguration` → 注入 `HbaseComponent` → 建表/put/get/scan/delete 全链路。严格遵循 TDD 红-绿循环：先写测试并亲眼看到失败（预期暴露已知缺陷：`HbaseSingleFamilyOperation.put()`/`putList()` 的 try-with-resources 把共享 `Connection` 一并关闭，首次 put 后所有后续操作抛 `IllegalArgumentException: Connection is null or closed.`），再做最小修复转绿。

环境约束（已核实/本机实证的事实）：
- 测试设计遵循 `.rick/jobs/job_1/plan/prompts/skill_tdd_zh.md`（红-绿循环）与 `.rick/jobs/job_1/plan/prompts/skill_testing_anti_patterns_zh.md`（零 mock、不加仅测试用的生产方法）。
- **测试 JVM 必须用 JDK 8（前置约束，非预案）**：本机实证 hbase-shaded-client 1.2.0 在 JDK 21 上创建 Connection 必败——第一层 `Bytes$LexicographicalComparerHolder$UnsafeComparer` clinit 反射 `java.nio.Bits` 被模块封装拦截抛 `java.lang.Error`（`--add-opens java.base/java.nio=ALL-UNNAMED` 可修）；第二层 `RpcClientImpl` clinit → `ClassSize` 用正则 `\d\.\d\..*` 匹配 `java.version`，JDK 21 报 `RuntimeException: Unexpected version format: 21.0.9`，**无任何 JVM 参数可修**（`-Djava.version` 无法覆盖 VM 内建值），JDK 11 同样失败。本机已装 zulu-8（1.8.0_472，`/usr/libexec/java_home -v 1.8` 可得）并实证通过。统一运行命令：`JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn test -Dtest=HbaseComponentIT`（编译+测试均在 JDK 8，与 source/target 8 天然契合）。**红阶段证据必须在 JDK 8 下采集**，否则看到的是上下文启动失败而非预期缺陷，归因不成立。
- pom.xml 无 parent/BOM，需显式声明依赖与插件版本（Maven 3.8.6 内置默认 surefire 2.12.4 过老，须显式 2.22.2）。
- 测试类命名 `*IT`，surefire 默认不包含，用 `mvn test -Dtest=HbaseComponentIT` 显式运行（避免无 docker 环境时 `mvn test` 失败）。
- 测试不使用任何 mock（testing-anti-patterns：验证真实行为，HBase 为 task1 的真实容器）。
- slf4j 日志链：项目 slf4j-api 固定 2.0.16，而 spring-boot-starter 2.1.9 带入的 logback-classic 1.2.3 是 slf4j 1.7 绑定机制，slf4j 2.0 找不到 provider → **全部日志静默丢弃**，KR 中"预期内 error 业务日志"将不可见。修复：pom 增加 test scope `org.slf4j:slf4j-simple:2.0.16`（slf4j 2.0 provider，JDK 8 兼容，零配置输出到 stderr；logback 1.4.x 要求 JDK 11 不可用，1.3.x 可作备选）。
- `ConnectionFactory.createConnection` 为懒连接：HBase 未就绪时 Spring 上下文仍能启动，失败延后到首个 RPC——红绿归因时须知悉此行为（测试方法前置的 master-status 检查已兜底）。
- region 分配竞态：`HbaseAdminOperation.createTable` 不等 region 分配完成；retries=5 × pause=100ms 的重试窗口仅约 1-3 秒，amd64 模拟环境下建表后首次 put 偶发 `RetriesExhaustedException`（非缺陷、非配置错误，会干扰红绿归因）→ `@Before` 建表后必须用 `admin.isTableAvailable(...)` 轮询（1 秒间隔、上限 60 秒）再进入用例。此为测试侧等待，不属于"仅测试用的生产方法"。

# 关键结果
1. pom.xml 新增：`spring-boot-starter-test:2.1.9.RELEASE`（test scope，与 main 中 spring-boot 版本对齐，自带 JUnit 4.12）；`org.slf4j:slf4j-simple:2.0.16`（test scope，见环境约束）；`maven-surefire-plugin:2.22.2` 显式声明。`JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn -q test-compile` 成功。
2. `src/test/java/com/github/xuning888/hbase/HbaseComponentIT.java` 存在：
   - `@RunWith(SpringRunner.class)` + `@SpringBootTest(classes = XnHbaseAutoConfiguration.class, properties = {...})`；
   - properties 与 `deploy/README.md` 接入样例一致：`xn.hbase.enabled=true`、`xn.hbase.zookeeper-quorum=hbase`、`xn.hbase.zookeeper-property-client-port=2181`、`xn.hbase.auth.enabled=false`、`xn.hbase.rpc-timeout=60000`、`xn.hbase.client-retries-number=5`；
   - 注入 `HbaseComponent`，调用路径：CRUD 经 `hbaseComponent.getHbaseSingleFamilyOperation()`（put/get/putList/scan/delete），建/删表与 `isTableAvailable` 经 `hbaseComponent.getHbaseAdminOperation()` 及 `hbaseComponent.getHbaseConn().getAdmin()`；
   - 表名固定 `test_it`、列族 `cf1`；`@Before` 若 `test_it` 存在先删再建、建表后 `isTableAvailable` 轮询等待（1s 间隔、上限 60s）、并确保 `no_such_table` 不存在（存在则删，保证异常路径用例可重复执行）；`@After` 删 `test_it`。
3. 红阶段证据留存：修复前在 **JDK 8** 下运行 `mvn test -Dtest=HbaseComponentIT` 的失败输出被记录。预期签名：put 成功返回 true，其后 get/scan 抛 `IllegalArgumentException: Connection is null or closed.`（HTable 构造器对已关闭连接的确切行为，已字节码确认）；`@Before` 的 admin 路径（tableExists）在红阶段被 `catch (Exception)` 吞掉静默返回 false 属预期。级联说明（JUnit4 方法执行顺序非源码顺序）：仅"最先执行的含 put/putList 的用例"呈现主签名；其后执行的用例允许出现 (a) put/putList 返回 false 的断言失败、(b) `@Before` 的 `isTableAvailable` 轮询在已关闭连接上抛 IOException 报错。归因判定统一为：所有失败/报错均可追溯到共享 Connection 被首次 put 关闭，方可确认"因功能缺陷失败"；出现无法追溯到该缺陷的失败（如连不上 ZK、超时）视为环境/配置问题，须先排除再采集红证据。
4. 绿阶段最小修复：`HbaseSingleFamilyOperation.put()` 与 `putList()` 的 try-with-resources 中移除 `Connection`（仅关闭 `Table`），不改动其他逻辑、不加新功能。
5. `JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn test -Dtest=HbaseComponentIT` 全部用例通过（Tests run: 4, Failures: 0, Errors: 0）；stderr 日志（slf4j-simple）中除异常路径用例（测试方法 4）预期内的 error 级别业务日志（`HbaseSingleFamilyOperation.get()` 先记日志再抛异常，属源码既定行为）外，无意外 ERROR/未捕获异常堆栈。
6. `JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn install -DskipTests` 执行成功，构件 `com.github.xuning888:hbase-spring-boot-starter:1.0-SNAPSHOT` 安装到本地仓库（`~/.m2/repository`），供用户自己的 Java 程序直接依赖（README"在自己的项目中使用本 starter"章节引用此步骤）。
7. 每个测试用例覆盖四要素（前置条件/输入参数/操作序列/预期输出），断言为可量化的具体值；注意 scan 返回 `List<Map<String,String>>` 仅含 qualifier→value，**rowKey 不在结果中**，按 Map 值断言。

# 测试方法
1. 正常路径（全链路 CRUD + 连接复用回归）：前置条件=task1 的 HBase 就绪（`curl -sf http://localhost:16010/master-status` 返回 200）；`/etc/hosts` 含 `127.0.0.1 hbase`；表 `test_it` 已按 `@Before` 建好且 `isTableAvailable` 通过、无数据。输入=put(`test_it`, `rk001`, `cf1`, {name=张三, age=30}) → get(`test_it`, `rk001`, `cf1`) → delete(`test_it`, `rk001`, `cf1`) → get 同 rowKey。操作=同一 Spring 上下文内，经 `hbaseComponent.getHbaseSingleFamilyOperation()` 依次调用上述方法。预期输出=put 返回 true；get 返回 Map 恰为 `{name=张三, age=30}`；delete 返回 true；再次 get 返回 null（familyMap 为 null）。回归点：put 之后的所有操作在同一连接上成功——红阶段此处抛 `IllegalArgumentException: Connection is null or closed.`，修复后通过。
2. 边界用例（get 不存在的 rowKey）：前置条件=表 `test_it` 存在、列族 `cf1`、无 `not_exist_rk` 行。输入=get(`test_it`, `not_exist_rk`, `cf1`)。预期输出=返回 null，不抛异常。
3. 边界用例（putList + scan 的 limit 与 startRow 排他）：前置条件=空表 `test_it`。输入=putList 写入 3 行：`rk_a`/`rk_b`/`rk_c`（列族 `cf1`，familyMap 分别为 {idx=a}/{idx=b}/{idx=c}）；scan(`test_it`, startRow=`rk_a`, stopRow=null, family=`cf1`, limit=2)；再 scan(`test_it`, startRow=`rk_a`, stopRow=null, family=`cf1`, limit=10, startRowInclusive=false)。预期输出=putList 返回 true；第一次 scan 返回恰 2 行，按顺序 `list.get(0)` 等于 `{idx=a}`、`list.get(1)` 等于 `{idx=b}`（PageFilter 生效，rowKey 不在结果 Map 中，按值断言）；第二次 scan 返回恰 2 行，按顺序分别为 `{idx=b}` 与 `{idx=c}`，不含 `{idx=a}`（RowFilter 排他生效）；同时覆盖 putList 的连接复用修复。
4. 异常路径（操作不存在的表）：前置条件=HBase 中不存在表 `no_such_table`（`@Before` 确保已删）。输入=get(`no_such_table`, `rk001`, `cf1`)。预期输出=抛出 `IOException`（`TableNotFoundException`，异常消息含表名 `no_such_table`），用 `@Test(expected = ...)` 或 try/catch 断言异常类型与消息，验证错误路径行为而非静默吞错。
