# frontend

Marelight 的静态前端，由 Windows 版 Nginx 托管，并反向代理后端接口。

项目总览、MySQL 与 Redis 的准备见仓库根目录 [README.md](../README.md)。

## 目录

```
frontend/
├── nginx.exe              本地启动用的 Nginx 二进制（需放在本目录）
├── conf/nginx.conf        监听 8080，托管页面并代理 /api
├── html/hmdp/             页面、样式、脚本
│   ├── js/common.js       Axios 基址、Token 头、401 跳转
│   └── css/
├── html/50x.html          Nginx 错误页
└── logs/、temp/           运行时目录，不入库
```

`conf/`、`contrib/`、`docs/` 来自 Nginx 发行包。业务页面都在 `html/hmdp/`。

## 页面

| 文件 | 用途 |
| --- | --- |
| `index.html` | 首页 |
| `shop-list.html` | 店铺列表 |
| `shop-detail.html` | 店铺详情、优惠券 |
| `blog-edit.html` | 发布探店 |
| `blog-detail.html` | 探店详情 |
| `login.html`、`login2.html` | 登录 |
| `info.html`、`info-edit.html` | 个人中心与资料编辑 |
| `other-info.html` | 他人主页 |

页面使用 Vue 2、Element UI、Axios，脚本直接写在 HTML 里，没有单独的前端构建步骤。

## 请求约定

`html/hmdp/js/common.js` 把 Axios 的 `baseURL` 设为 `/api`。登录后 Token 存在 `sessionStorage`，请求头名为 `authorization`。接口返回 `success: false` 时按 `errorMsg` 拒绝；HTTP 401 会跳到 `/login.html?v=2`。

Nginx 去掉 `/api` 前缀后再转发。浏览器访问 `/api/shop-type/list`，后端收到的是 `/shop-type/list`。

`conf/nginx.conf` 中：

- `listen 8080`，站点根目录 `html/hmdp`
- `location /api` 使用 `rewrite /api(/.*) $1 break`，再 `proxy_pass http://backend`
- `upstream backend` 目前包含 `127.0.0.1:8081` 和 `127.0.0.1:8082`

只启动一个后端实例时，8082 不可达。需要单实例联调时，把 upstream 里未启动的那一行去掉，或先把第二个实例也跑起来。

## 启动与停止

必须在本目录启动，配置里的路径相对 `frontend/`：

```powershell
cd frontend
.\nginx.exe
```

浏览器打开 [http://localhost:8080](http://localhost:8080)。后端需已在 `8081`（以及 upstream 里配置的其它端口）监听，否则页面能开、接口会失败。

```powershell
.\nginx.exe -s reload   # 改完 nginx.conf 后重载
.\nginx.exe -s stop     # 停止
```

探店图片由后端写到 `SystemConstants.IMAGE_UPLOAD_DIR`。该路径需要指向本机的 `frontend/html/hmdp/imgs/`，否则上传成功后页面也访问不到文件。当前常量仍是另一台机器上的绝对路径，联调前改成你本机的目录。
