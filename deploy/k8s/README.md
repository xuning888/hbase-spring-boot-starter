# HBase on Kubernetes (minikube)

本地 minikube 上运行单机 HBase 1.2，用于开发测试。

## 快速启动

```bash
# 1. 确保 minikube 已启动
minikube start --driver=docker --cpus=4 --memory=4096

# 2. 部署 HBase
kubectl apply -f deploy/k8s/hbase.yaml

# 3. 等待就绪（约 1-3 分钟首次启动，需拉镜像）
kubectl wait --for=condition=ready pod -l app=hbase --timeout=300s

# 4. 验证
kubectl port-forward svc/hbase 16010:16010 &
curl -s http://localhost:16010/master-status | head -20
```

## 本地连接方式

### 方式 A：port-forward（推荐，不用改 hosts）

在一个终端里保持运行：

```bash
kubectl port-forward svc/hbase 2181:2181 16000:16000 16010:16010
```

然后应用连接 `localhost:2181` 即可。

### 方式 B：NodePort + /etc/hosts

```bash
# 获取 minikube IP
minikube ip          # 例如 192.168.49.2

# 获取自动分配的 NodePort
kubectl get svc hbase -o jsonpath='{.spec.ports[0].nodePort}'  # ZK 端口

# 加到 /etc/hosts（一次性）
echo "$(minikube ip) hbase" | sudo tee -a /etc/hosts

# 之后应用直接用 hbase:2181 连接
```

## 数据持久化

| Volume | 宿主机路径 | 容器路径 | 用途 |
|--------|----------|---------|------|
| hbase-data | `/data/hbase-data` | `/hbase-data` | HDFS/HBase 数据 |
| zk-data | `/data/hbase-zk-data` | `/tmp/hbase-root` | ZooKeeper 状态 |

`hostPath` + `DirectoryOrCreate` 确保 pod 重启/重建后数据不丢失。

> **minikube 注意**：`hostPath` 在 minikube 里是 VM 内部路径，不是 macOS 路径。
> 如果 minikube 重启（`minikube stop && minikube start`），数据保留在 VM 里不会丢。
> 只有 `minikube delete` 才会清空。

## 清理

```bash
kubectl delete -f deploy/k8s/hbase.yaml

# 如果要清掉持久化数据：
minikube ssh -- "sudo rm -rf /data/hbase-data /data/hbase-zk-data"
```
