# 登录界面代码整理包

这个仓库现在同时保留了两部分内容：

- `src/`：原始 React + Supabase 版本的登录界面整理包，方便你继续参考之前项目的实现思路
- `src/main/`：重新落地的 Java 后端版可运行项目，采用 Spring Boot 直接提供登录页、注册页、找回密码页、重置密码页和管理员背景管理页

## 为什么改成 Java 后端版

如果你的目标是：

- 本地直接运行，少依赖第三方控制台
- 统一管理登录、角色、上传和图片配置
- 后续继续扩展成完整 Java 网站后台

那么改成 Java 后端是现实且可行的。

这次落地采用的是：

- Spring Boot 3
- Spring Security
- Spring MVC + Thymeleaf
- Spring Data JPA
- H2 文件数据库

## 当前 Java 版本包含的能力

- 登录页
- 注册页
- 找回密码页
- 重置密码页
- 首页状态展示
- 管理员背景图管理页
- 本地图片上传
- 背景图外链添加
- SHA-256 文件级去重
- 最低分辨率与清晰度校验
- 固定背景 / 随机背景切换
- 前端 `localStorage` 避免连续两次随机到同一张图
- 本地文件删除清理
- 启动时自动初始化管理员账号

## 关键目录

- `pom.xml`：Maven 项目入口
- `src/main/java/com/example/logininterface/`：Java 后端代码
- `src/main/resources/templates/`：Thymeleaf 页面模板
- `src/main/resources/static/`：样式与前端脚本
- `data/`：运行后自动生成的本地数据库和上传文件目录

## 启动方式

先确保本机安装：

- JDK 21
- Maven 3.9+

先打包：

```bash
mvn package
```

再运行：

```bash
java -jar target/login-interface-package-0.0.1-SNAPSHOT.jar
```

启动后访问：

```text
http://localhost:8081
```

## 一键启动与关闭脚本

项目根目录已经补充了两个 Windows 脚本：

- `开始项目.bat`：双击后会优先尝试用 Maven 打包最新代码，然后在后台启动项目，并自动打开浏览器
- `结束项目.bat`：双击后会读取记录的 PID，或按 `8081` 端口查找进程并关闭

日志默认写到：

```text
logs/app.log
logs/app-error.log
```

说明：

- 如果你本机已经装了 Maven，`开始项目.bat` 会先执行 `mvn -DskipTests package`
- 如果你没有 Maven，但项目里已经存在 `target/login-interface-package-0.0.1-SNAPSHOT.jar`，脚本也可以直接启动
- 如果端口 `8081` 已被别的程序占用，启动脚本会提示你先关闭占用进程

## 默认管理员账号

- 邮箱：`admin@example.com`
- 密码：`Admin123456`

这些默认值可以在 `src/main/resources/application.yml` 里修改。

## Java 方案与原 Supabase 方案的取舍

### 优点

- 所有逻辑都在自己项目里，调试链路更直观
- 管理员账号、图片上传、角色控制都能直接在 Java 后端统一处理
- 更适合你后面继续往 Spring Boot 全站方向扩展
- 本地开发时不用先依赖 Supabase Storage、RLS 和 Dashboard

### 缺点

- 需要自己维护数据库、密码安全、权限控制和重置流程
- 邮件验证码、OAuth 第三方登录这些高级能力，不如 Supabase 开箱即用
- 生产环境部署时，你要自己准备数据库、文件存储和备份方案

## 目前和原方案的差异

为了保证“本地快速跑起来”，当前 Java 版做了两个简化：

- 找回密码使用“调试用重置链接”而不是邮件发送
- 图片存储改成本地文件夹而不是 Supabase Storage

如果你后面要上正式环境，可以继续演进成：

- MySQL / PostgreSQL
- MinIO / 阿里云 OSS / 腾讯云 COS
- JavaMail 或第三方邮件服务
- 更完整的用户资料页和后台管理

## 原始参考代码仍然保留

如果你还想继续参考之前那个你觉得“效果不错”的版本，原始文件仍在这些位置：

- `src/pages/Login.tsx`
- `src/pages/Register.tsx`
- `src/pages/ForgotPassword.tsx`
- `src/pages/ResetPassword.tsx`
- `src/components/AuthLayout.tsx`
- `src/hooks/useSiteBackground.ts`
- `src/lib/loginBackground.ts`
- `src/pages/UserProfile.tsx`
- `src/context/AuthContext.tsx`
- `supabase/migrations/*.sql`
