# backend

Marelight 的 Spring Boot 服务。默认监听 `8081`，由前端 Nginx 把 `/api/*` 转到这里。

项目总览、MySQL 导入和 Redis 容器见仓库根目录 [README.md](../README.md)。

## 环境

- JDK 21。`java -version` 必须是 21，用 JDK 8 无法编译和启动。
- Maven 3.9+
- MySQL 8，库名 `marelight`
- Redis 7，本仓库用 Docker 映射到 `127.0.0.1:26379`

技术线：Spring Boot 4.1.1、`spring-boot-starter-webmvc`、MyBatis-Plus 3.5.17、Hutool 5.7.17、Lettuce、Redisson 4.0.0。Servlet API 为 Jakarta。

## 启动

先改 `src/main/resources/application.yaml` 里的 MySQL 密码，使其与本机一致。Redis 已按根目录 `docker-compose.yml` 写好：端口 `26379`，密码 `marelight`。

```powershell
cd backend
mvn spring-boot:run
```

入口类是 `com.jacobzp.MarelightApplication`。启动后访问 `http://127.0.0.1:8081`。

验证码不走短信网关，成功时打在 `com.jacobzp` 的 debug 日志里。登录只校验这 6 位数字，表单里的密码字段尚未使用。新手机号会自动建用户，昵称前缀为 `ml_`。

## 配置

`src/main/resources/application.yaml`

| 项 | 当前值 |
| --- | --- |
| `server.port` | `8081`，需与 Nginx `upstream` 一致 |
| `spring.datasource` | `127.0.0.1:3306/marelight`，用户 `root` |
| `spring.data.redis` | `127.0.0.1:26379`，密码 `marelight` |
| 日志 | `com.jacobzp` 为 `debug` |

Boot 4 使用 `spring.data.redis`，不再使用 `spring.redis`。

`src/main/java/com/jacobzp/utils/SystemConstants.java` 里的 `IMAGE_UPLOAD_DIR` 要改成你本机的 `frontend/html/hmdp/imgs/` 绝对路径，探店图片才会被 Nginx 访问到。

不要把真实数据库密码提交进仓库。

建库脚本：`src/main/resources/db/marelight.sql`。导入方式见根目录 README。

## 代码结构

```
src/main/java/com/jacobzp/
├── MarelightApplication.java
├── controller/     HTTP 接口
├── service/        接口与 impl
├── mapper/         MyBatis-Plus Mapper
├── entity/         表对应实体
├── dto/            入参、统一响应 Result、登录会话
├── config/         MVC 拦截器、Redis、MyBatis、异常处理
├── mq/             秒杀订单的 Redis Stream 消费者
└── utils/          缓存、锁、Token、常量
```

`src/test/java` 里有店铺缓存和秒杀异步对比的测试。

## 接口

浏览器侧要加 `/api` 前缀。下面是后端实际路径。

| 前缀 | 已实现 |
| --- | --- |
| `/user` | `POST /code`、`POST /login`、`POST /logout`、`GET /me`、`GET /info/{id}` |
| `/shop` | `GET /{id}`、`POST`、`PUT`、`GET /of/type`、`GET /of/name` |
| `/shop-type` | `GET /list` |
| `/blog` | `POST`、`PUT /like/{id}`、`GET /of/me`、`GET /hot` |
| `/voucher` | `POST`、`POST /seckill`、`GET /list/{shopId}` |
| `/voucher-order` | `POST /seckill/{id}`、`POST /seckill/async/{id}` |
| `/upload` | `POST /blog`、`GET /blog/delete` |
| `/follow`、`/blog-comments` | 只有类映射，方法未接 |

统一响应是 `com.jacobzp.dto.Result`：`success`、`errorMsg`、`data`。

## 登录与缓存

`MvcConfig` 注册两个拦截器：

1. `RefreshTokenInterceptor` 匹配全部路径。请求带 `authorization` 时，用 `login:token:{token}` 从 Redis 还原用户，并刷新 36000 秒过期时间。
2. `LoginInterceptor` 对未登录请求返回 HTTP 401。白名单：`/user/code`、`/user/login`、`/blog/hot`、`/shop/**`、`/shop-type/**`、`/upload/**`、`/voucher/**`。

验证码键为 `login:code:{手机号}`，有效期 2 分钟。

店铺详情 `GET /shop/{id}` 经 `CacheClient`：键 `cache:shop:{id}`，30 分钟再加 0 到 5 分钟随机量；未命中时用 `lock:shop:{id}` 互斥；库中没有则缓存空值 2 分钟。更新店铺后删除缓存。分类和列表仍直查 MySQL。

秒杀同步下单 `seckillVoucher5` 用 Redisson 锁 `lock:order:{userId}` 做一人一单。异步下单 `seckillVoucher6` 用 Lua 判断库存和是否已购，订单写入 `stream.orders` 后立即返回，由 `VoucherOrderStreamConsumer` 落库。页面上的抢购按钮仍调用同步接口。
