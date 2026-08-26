# Issue #25 数据库资产验证记录

验证时间：2026-08-26（Asia/Shanghai）

## 自动化测试

```text
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

命令：`cd backend && mvn -B -ntp test`

其中新增的 `DatabaseAssetsTest` 验证演示用户和管理员密码 hash 可由 Spring Security BCrypt 正确匹配，并验证商品固定 ID 与当前内存基线一致。

## MySQL 实际执行

本地使用隔离临时数据目录和 MySQL 8.0.45 执行 `V001__baseline_schema.sql`、两次 `demo-data.sql`、`clean-data.sql` 和清理后的再次 seed。临时实例验证完成后已关闭，数据目录已移入回收站；未读写系统 MySQL 实例的数据。

```text
# schema table count, user count, product count, group deal count
17,6,7,3

# cleanup 后 user count, product count
0,0

# 再次 seed 后 user count, product count
6,7
```

结论：Schema 可从空库创建；seed 可重复执行且不产生重复数据；清理脚本满足外键顺序要求；清理后可重新装载演示数据。

## 静态检查与 CI 门禁

- `git diff --check`：通过。
- Compose 与 GitHub Actions YAML 解析：通过。
- `database/bin/*.sh` POSIX shell 语法：通过。
- CI 新增 MySQL 8.4 实跑：两次迁移、两次 seed、验证、清理、再次 seed 和再次验证。该门禁将在 PR/远端流水线中提供 MySQL 8.4 证据。

本地环境未安装 Docker，因此未声称已在本机执行 Compose；MySQL 8.4 镜像验证由新增 CI job 负责。
