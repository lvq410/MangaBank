# 基础镜像
FROM adoptopenjdk/openjdk8:jdk8u392-b08-alpine

#使用国内的源
RUN echo -e 'https://mirrors.aliyun.com/alpine/v3.9/main/\nhttps://mirrors.aliyun.com/alpine/v3.9/community/' > /etc/apk/repositories

# 更新源
RUN apk update
# 安装bash，测试阶段为了调试可以选装
RUN apk add bash
# 安装必要网络工具，测试阶段为了调试可以选装
RUN apk add curl
RUN apk add busybox-extras
RUN apk add net-tools
RUN apk add --no-cache nss

# 将打包好的项目添加到镜像中
WORKDIR /app
ADD . ./

# 添加脚本的执行权限
RUN chmod +x ./start.sh
RUN chmod +x ./libwebp-1.4.0-linux-x86-64/bin/cwebp

ENV cwebp.path=/app/libwebp-1.4.0-linux-x86-64/bin/cwebp

# 设置项目启动时脚本
ENTRYPOINT ["sh","-c","./start.sh"]