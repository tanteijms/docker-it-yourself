# docker-it-yourself
本项目为 BUPT BYR 技术组后端面试题：实现简化版 Docker Registry V2，支持 Blob 上传校验、Manifest 管理及基本错误处理。

Interview task for BUPT BYR backend: simplified Docker Registry V2 with blob upload verification, manifest management, and basic error handling.



> test目录为自动计算当前文件的字节大小，以及sha256值的一个简易python gui脚本。conda环境为：file2sha256auto
> new：不需要conda环境依赖，python基本环境就行

注：已废弃，使用Powershell自带的sha256算法即可



## report

### 设计思路

首先审题发现需要在linux环境下通过测试。我没有相关的项目经历，wsl和vmware Ubuntu也没有做过大规模项目的开发及测试。在开发语言与技术栈的选择上一开始考虑go+gin，按理说是最适合docker开发生态的。但不熟悉go该如何部署到linux上并进行测试，最终选择了比较熟悉的java spingboot框架来实现，且得益于java的跨平台部署，我只需要在win本机开发测试完成以后根据linux的yml配置文件打包好即可。

本题要求实现docker的文件会话上传，以及分片功能。我首先考虑到了用mybatis+mysql存储会话信息，并在最终complete upload时采取阿里云oss对象存储来完成整个的上传，并存储到云仓库里。具体需要设计1. Blob实体类  2. Manifest实体类，包含镜像的所有层信息、配置文件等  3. 上传会话，upload session，由于一次上传可能涉及多个分片，因此需要会话来进行统一关系。

这三个实体同样设计到了mysql的数据库里进行存储

项目的整体设计为经典的springboot+mybatis+mysql设计。控制器负责关于一切的web信息，设计是符合REST api的；控制调动service服务层，通过serviceImpl的实现类来完成三大实体的具体业务实现；并将相关数据转发到mapper层通过mybatis实现持久化，存入到mysql数据库里。当一次上传完成以后，便最终上传到阿里云oss对象存储。

测试使用postman写接口测试来实现。在调试时，由于没有相应的客户端实现，因此需要手动上传文件，并在http请求的body部分上传二进制文件，测试时采用txt。然后还需要对文件进行sha256校验，本来写了一个python脚本能够一键生成，后来发现好像有点问题，弃用了。改用powersehll自带的算法来计算。需要手动在参数里设置当前文件的sha256值，以及文件所占的字节大小。这个是因为，分片有点类似于网络传输里的分片，告知当前文件大小，后端服务可以判断下一个预期的字节序号，并进行文件校验。



### 遇到的问题

1. 一开始写的python脚本用于快速生成对应文件的sha256值，以及文件大小（字节），疑似有问题，测试的时候每次和后端服务算出来的不一样导致校验失败。最后弃用

2. 完善自定义异常类

   PUT请求使用不支持的Content-Type，期望返回415 Unsupported Media Type 以及错误的JSON，而不应该返回空响应

   创建一个自定义异常类，专门处理不支持的媒体类型错误

   修复异常处理器，能够返回标准415错误响应

3. 思考mysql存储会话记录的必要性

   如果直接用内存呢？或者用redis呢？或许性能上会有提升。最开始开发的时候惯性思维了直接上springboot+mybatis，毕竟镜像文件是直接存储进阿里云oss里了，本地临时存储似乎也没必要用mysql

4. postman api测试文件的编写

   不是很熟、、很多bug修来修去才完全测试跑通

5. http所有请求

   以前没接触过head、range等，而这里对于镜像文件的上传，如果仓库里是一致的，就不做修改。因此不能用post要用patch

6. 编写linux环境的yml配置文件

   mysql的连接，windows直接用localhost连接，但linux需要用docker访问宿主机的mysql

   linux不配置连接池参数

   主要是linux为了在docker容器中运行



### 优缺点分析

#### 优点

1. 套用springboot经典框架，controller-service-mapper

2. 存储方案：以前用过的熟悉的阿里云oss对象存储，且进行了路径的优化，用前缀分层，有一定的查找性能

   对于分片，用阿里云oss的appendObject()高效合并

3. java跨平台，解决我调试linux环境经验不足的问题



#### 缺点

1. mysql存临时上传会话，确实有点冗余了，直接走内存的话应该性能好一些
2. 简单的后端服务，没有token验证，安全性考量
3. 缺少缓存：热点数据下载每次都访问oss，以及sql，没有缓存
4. 测试不够：只进行了postman手工测试，没有jmeter或pytest自动化集成测试



### 心路历程

一开始是想用go写的，但自己没那么熟，且一点不会go的那些框架。本来想着速成一下，最后还是懒了，就springboot了，同样也省去了linux环境的问题。不过如果有时间的话，还是会用go重新写一下

选diy看到题目是文件上传还挺轻松的，因为写java spring的项目写过太多上传功能了。不过调的时候为了符合docker规范还是花了一些时间，毕竟和平时的业务逻辑不太一样

纯后端测试还是不是很熟。以前直接手搓简单前端然后直接上现成的业务。postman这次也是一点点改api然后学会测