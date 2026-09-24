# Marelight

本地生活发现平台。逛店铺、写探店、领优惠，把附近值得去的地方点亮。

当前仓库已纳入前后端模块，业务能力按阶段推进。打开本文件即可了解项目是什么、怎么跑、代码在哪、接下来做什么。

[功能概览](#功能概览) · [技术栈](#技术栈) · [架构](#架构) · [仓库结构](#仓库结构) · [快速开始](#快速开始) · [配置](#配置) · [当前进度](#当前进度)

---

## 功能概览

面向「附近好去处」这一场景，页面与接口已按以下模块铺开：

| 模块 | 说明 |
| --- | --- |
| 店铺 | 分类浏览、按类型/名称检索、查看店铺详情 |
| 探店 | 热门笔记流、发布图文、点赞、个人主页 |
| 优惠券 | 店铺优惠券与秒杀券下单入口 |
| 用户 | 手机号验证码登录、资料、关注 |
| 签到 | 用户签到相关数据表与 Redis Key 预留 |

前端已覆盖首页、店铺列表/详情、探店编辑与详情、登录、个人中心等页面。后端短信验证码登录与 Redis Token 会话已接通；秒杀等高并发能力仍待实现。

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 2、Element UI、Axios；由 Nginx 托管静态页并反向代理 API |
| 后端 | Java 21、Spring Boot 4.1.1、Spring MVC（`spring-boot-starter-webmvc`）、MyBatis-Plus 3.5.17、Hutool |
| 数据 | MySQL 8（`mysql-connector-j`）、Redis 7（Lettuce 连接池） |
| 运行 | 后端 `8081`，Nginx `8080`，浏览器只访问前端端口 |

后端已从 Spring Boot 2.3 / Java 8 升到当前线。Servlet API 使用 Jakarta（`jakarta.servlet` / `jakarta.annotation`），Redis 配置在 `spring.data.redis` 下。

---

## 架构

```
浏览器
  │  http://localhost:8080
  ▼
Nginx（frontend/）
  ├─ 静态资源  html/hmdp/
  └─ /api/*  ──rewrite──►  Spring Boot :8081
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
                 MySQL                Redis
                 库 marelight
```

请求约定：前端 Axios 的 `baseURL` 为 `/api`，Nginx 去掉 `/api` 前缀后转发到后端。因此后端接口形如 `/shop-type/list`，浏览器侧则为 `/api/shop-type/list`。

已登录请求会带 `authorization` 头。后端先由 `RefreshTokenInterceptor` 按 Token 从 Redis 还原用户并刷新过期时间，再由 `LoginInterceptor` 拦截未登录访问。公开接口（发码、登录、热门探店、店铺、分类、优惠券查询、上传）不要求登录。

---

## 仓库结构

```
Marelight/
├── docker-compose.yml            本项目专用 Redis 容器
├── backend/                      Spring Boot 服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/jacobzp/    启动类、controller / service / mapper / entity
│       └── resources/
│           ├── application.yaml  端口、数据源、Redis
│           ├── db/marelight.sql  建库脚本、表结构与示例数据
│           └── mapper/           MyBatis XML
└── frontend/                     Windows 版 Nginx + 静态前端
    ├── nginx.exe
    ├── conf/nginx.conf           监听 8080，代理 /api
    └── html/hmdp/                Vue 页面、样式、脚本
```

后端入口：`com.jacobzp.HmDianPingApplication`。

主要 HTTP 前缀：

| 前缀 | 职责 |
| --- | --- |
| `/user` | 验证码、登录、当前用户、资料 |
| `/shop`、`/shop-type` | 店铺与分类 |
| `/blog`、`/blog-comments` | 探店与评论 |
| `/voucher`、`/voucher-order` | 优惠券与秒杀下单 |
| `/follow` | 关注 |
| `/upload` | 探店图片上传 |

---

## 快速开始

环境要求：JDK 21（`java -version` 需为 21）、Maven 3.9+、MySQL 8.0、Docker（用于本项目 Redis）、Windows（仓库内附带 `nginx.exe`）。不要用 JDK 8 启动，编译和运行都会失败。

### 1. 准备 MySQL

使用本机已安装的 MySQL，业务库名为 `marelight`。连接信息与 `backend/src/main/resources/application.yaml` 对齐：

| 项 | 值 |
| --- | --- |
| 地址 | `127.0.0.1` |
| 端口 | `3306` |
| 库名 | `marelight` |
| 用户名 | `root` |
| 密码 | 填本机 MySQL 的 root 密码 |
| 字符集 | `utf8mb4` |

导入 `backend/src/main/resources/db/marelight.sql`。脚本会创建库 `marelight` 并写入表结构和示例数据，适配 MySQL 8。

PowerShell 下不要用 `<` 重定向。在仓库根目录执行：

```powershell
mysql -u root -p --default-character-set=utf8mb4 -e "source backend/src/main/resources/db/marelight.sql"
```

导入后应有 11 张表。带示例数据的主要是店铺、分类、探店和用户，首页可直接出内容：

| 表 | 说明 |
| --- | --- |
| `tb_shop` / `tb_shop_type` | 店铺与分类 |
| `tb_blog` / `tb_blog_comments` | 探店笔记与评论 |
| `tb_user` / `tb_user_info` | 用户与资料 |
| `tb_voucher` / `tb_seckill_voucher` / `tb_voucher_order` | 优惠券、秒杀券、订单 |
| `tb_follow` / `tb_sign` | 关注、签到 |

用 DataGrip、Navicat 等客户端时，建库后请刷新连接；若仍看不到 `marelight`，到数据源的 Schemas / 数据库勾选列表里把它勾上。按库名排序时，它在 `mysql` 系统库上方。

### 2. 准备 Redis

本项目使用独立 Docker 容器 `marelight-redis`，避免和本机其它 Redis（默认 `6379` / `6380`）冲突。请先安装并启动 [Docker Desktop](https://www.docker.com/products/docker-desktop/)。

在仓库根目录启动：

```bash
docker compose up -d
```

首次会拉取 `redis:7-alpine` 并创建容器。启动成功后可用下面命令确认：

```bash
docker ps --filter "name=marelight-redis"
docker exec marelight-redis redis-cli -a marelight ping
```

后者应返回 `PONG`。

连接信息（已写入 `backend/src/main/resources/application.yaml`）：

| 项 | 值 |
| --- | --- |
| 地址 | `127.0.0.1` |
| 端口 | `26379`（不是 `6379`） |
| 密码 | `marelight` |
| 用户名 | 留空 |

用 Redis 客户端（如 Another Redis Desktop Manager）新建连接时，端口务必填 `26379`，并填写密码。

常用命令：

```bash
docker compose up -d      # 启动
docker compose stop       # 停止（数据保留）
docker compose down       # 删除容器（默认保留 volume）
```

数据目录为 Docker volume `marelight_marelight-redis-data`，容器重启不会丢数据。

### 3. 启动后端

按本机环境修改 `application.yaml` 里的数据源和 Redis 后再启动：

```bash
cd backend
mvn spring-boot:run
```

默认监听 `http://127.0.0.1:8081`。验证码不走短信网关，成功时打在 `com.jacobzp` 的 debug 日志里，把这 6 位数字填到登录页即可。登录只认验证码，表单里的密码字段尚未使用。

### 4. 启动前端

必须在 `frontend/` 目录下启动 Nginx，配置里的路径是相对该目录的：

```bash
cd frontend
.\nginx.exe
```

浏览器打开 [http://localhost:8080](http://localhost:8080)。

停止 Nginx：

```bash
cd frontend
.\nginx.exe -s stop
```

---

## 配置

改本地环境时，优先动这两处：

**后端** `backend/src/main/resources/application.yaml`

- `server.port`：默认 `8081`，需与 Nginx `proxy_pass` 一致
- `spring.datasource.*`：MySQL 地址、库名 `marelight`、账号密码（按本机填写，不要把真实密码提交进仓库）；驱动为 `com.mysql.cj.jdbc.Driver`
- `spring.data.redis.*`：Redis 地址、端口、密码（Boot 4 已不再使用 `spring.redis.*`）

**上传目录** `backend/src/main/java/com/jacobzp/utils/SystemConstants.java`

- `IMAGE_UPLOAD_DIR` 需指向本机前端静态图片目录，例如 `frontend/html/hmdp/imgs/` 的绝对路径，否则探店图片无法落到 Nginx 可访问的位置

不要把真实密码提交进仓库。本地覆盖即可。

---

## 当前进度

前后端已能联调。后端跑在 Spring Boot 4.1.1 + Java 21。店铺详情先查 Redis，未命中时抢互斥锁再查 MySQL；登录会话走 Redis。穿透、雪崩、逻辑过期和社交能力仍未接。

| 能力 | 状态 |
| --- | --- |
| 后端运行时（Boot 4.1 / JDK 21 / MyBatis-Plus 3.5） | 已升级，可启动 |
| 店铺分类、店铺查询/更新 | 详情 `GET /shop/{id}` 走 `cache:shop:{id}`（30 分钟）。未命中时用 `lock:shop:{id}` 互斥，同一时刻只放一个请求查库；更新后删除缓存。分类和列表仍直查 MySQL |
| 热门探店、发布、简单点赞 | 可用（未接 Redis 去重等） |
| 图片上传 | 接口已有，依赖本机上传目录配置 |
| 短信登录 / Token 会话 | 可用。验证码键 `login:code:{手机号}`（2 分钟），用户键 `login:token:{token}`（Hash，36000 秒）；无短信网关，验证码看 debug 日志；新手机号会自动建用户 |
| 登录拦截与登出 | 可用。刷新拦截器还原用户并续期，登录拦截器对非白名单返回 HTTP 401；登出删除 Redis Token |
| 优惠券秒杀下单 | 接口占位，返回「功能未完成」 |
| 关注、评论、签到、附近店铺 GEO、Feed | 表或 Key 已预留，业务未接 |

`RedisConstants` 里店铺详情缓存键和店铺互斥锁已在使用。空值 TTL、秒杀库存、点赞、Feed、GEO、签到等 Key 仍只是约定，后续按此扩展。

---

## 路线图

1. ~~**会话**：手机验证码登录、Token 存 Redis、登录拦截与登出~~（已完成）
2. **缓存**：~~店铺详情查询缓存~~（已完成）。~~击穿~~已用互斥锁处理。穿透、雪崩、逻辑过期仍未做
3. **秒杀**：库存预热、一人一单、异步下单
4. **社交**：点赞去重、关注、Feed 时间线
5. **附近与签到**：GEO 搜店、位图签到

欢迎按模块提交改动。提交前确认未把本机密码、日志和 `frontend/logs/` 打进版本库。
