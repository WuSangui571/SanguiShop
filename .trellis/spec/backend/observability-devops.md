# Observability & DevOps Guidelines

## Scope

适用于 Docker Compose、Kubernetes、CI/CD、Prometheus/Grafana、ELK/EFK、Zipkin/Jaeger、备份、配置和发布。

## Local Development

- 本地开发优先使用 `docker-compose.yml` 启动 MySQL、Redis、Nacos、MQ、Gateway、核心服务。
- 每个服务必须能通过环境变量覆盖数据库、Redis、Nacos、MQ、JWT、AI 配置。
- `.env.example` 只包含变量名和示例占位值，不包含真实 secret。

## Kubernetes Rules

- 每个服务使用 Deployment + Service。
- Gateway/前端入口使用 Ingress 或 Nginx。
- 配置使用 ConfigMap，密钥使用 Secret/Vault。
- 秒杀、订单、AI 服务必须可水平扩容。
- Redis、MySQL、MQ 生产环境优先使用托管服务或 StatefulSet + 持久卷。

## CI/CD Gates

```bash
mvn test
mvn -DskipTests package
npm run build   # 如果前端变更
```

推荐增加 CheckStyle/PMD/SonarQube、Dependency vulnerability scan、Docker image scan、Contract tests。

## Monitoring

Prometheus + Grafana Dashboard 至少覆盖 HTTP QPS/P95/P99/错误率、JVM、Redis、MQ、MySQL、AI 模型耗时、向量检索耗时、token 消耗。

## Backup and Disaster Recovery

- MySQL 每日全量 + 增量/binlog 备份。
- Nacos 使用 DB 存储并备份配置。
- MQ 开启持久化，核心 topic/queue 有保留策略。
- 定期演练恢复流程，不只配置备份。