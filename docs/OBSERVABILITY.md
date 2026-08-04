# 可观测性与容量保护

应用通过 `/actuator/prometheus` 暴露 JVM、HTTP、任务队列、任务状态、外部进程、磁盘容量和 NVIDIA GPU 显存指标。

## 启动 Prometheus

`monitoring/prometheus.yml` 默认抓取宿主机 `8081` 端口，适用于容器中的 Prometheus。若启动器为应用分配了其他端口，请同步修改 `targets`。告警规则位于 `monitoring/alerts.yml`，包括磁盘不足、队列积压、JVM 堆内存和显存压力。

Grafana 数据源和仪表盘配置位于 `monitoring/grafana`。将 `provisioning` 挂载到 Grafana 的 `/etc/grafana/provisioning`，将 `dashboards` 挂载到 `/var/lib/grafana/dashboards`，即可自动连接名为 `prometheus` 的 Prometheus 服务并加载中文运行监控仪表盘。

## 磁盘保护

默认要求素材存储目录至少保留 5 GiB 且保留 5% 可用空间。创建本地或远程任务前，系统会先执行一次安全缓存清理；仍不足时返回 HTTP 507，不再接收新任务。可通过以下环境变量调整：

- `MINIMUM_FREE_STORAGE_BYTES`
- `MINIMUM_FREE_STORAGE_PERCENT`
