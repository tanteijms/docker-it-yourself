## docker

容器

标准化环境



镜像是只读的模板，template

静态



容器时镜像的运行实例

动态、有生命周期

同一个镜像可以有多个容器，且各个容器之间相互隔离



docker client，与用户交互，接收docker命令

docker daemon  docker核心服务

docker engine运行不同的容器



docker镜像，层级结构

不是一个单一文件，时多个层叠加组成



如：

ubuntu  基础层

jdk  软件安装层

app  应用程序层

yml配置文件  配置层

java编译之  启动命令层



这样可以复用，多个镜像对于基础层、软件安装的，可以使用共享的



## 下载并分析一个镜像

```bash
docker --version

docker pull alpine:latest # Alpine是一个非常小的Linux发行版，几MB

docker ps # 查看容器列表
```

需要先启动docker desktop

```bash
docker image inspect alpine:latest

docker history alpine:letest # 查看各个层的构筑
```

IMAGE ID  类似git的commit hash，每个镜像都有唯一的id



镜像可以压缩，Registry传输时使用压缩格式，可以节省带宽