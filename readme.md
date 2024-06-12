# 漫吧

一个简单的漫画浏览与管理工具

# 特性
* 基于[webp](https://developers.google.com/speed/webp)压缩工具，支持图片多分辨率压缩浏览
* 基于[lucene](https://github.com/apache/lucene)搜索引擎，支持为漫画打标签，配置多标题，并以此为基础进行搜索
* 多用户特性，该功能较弱，目前不同用户的区别仅可收藏的本子不同

# 部署
## 机器环境
1. 确保机器环境有java8+，webp
1. [Release](https://github.com/lvq410/MangaBank/releases)里下载zip包，解压
1. 根据需要调整config/application.yml配置，以及start.sh里的java命令位置等
1. 执行start.sh即可

## docker
1. 镜像为 `lvq410/manga-bank:{version}` [hub.docker](https://hub.docker.com/r/lvq410/manga-bank)
1. 可参考[Release](https://github.com/lvq410/MangaBank/releases)包里的config/application.yml配置文件，通过调整环境变量来调整配置

# 初始化

如果是初次启动，会自动创建一个`admin`用户，其密码同为`admin`

管理后台的访问地址为：[http://localhost:8080/admin/index.html](http://localhost:8080/admin/index.html)

这里可以管理用户、标签、修改漫画本子的标题，标签等

如果在服务未运行期间，调整了漫画库文件目录中的文件，需要手动到后台的 【本子管理】中，点击【手动同步】进行同步

漫画浏览的访问地址为：[http://localhost:8080/index.html](http://localhost:8080/index.html)

# 支持文件类型

图片文件格式仅支持：jpg, jpeg, png, gif, bmp

压缩包文件格式仅支持：zip

# 漫画库文件目录结构

在配置`book.dir`所指向的文件夹下

```
├── 漫画1 （图片构成的文件夹）
│   ├── 1.jpg
│   └── 2.jpg
├── 漫画2.zip （图片压缩包）
├── 漫画3 （图片压缩包构成的文件夹）
│   ├── 卷1.zip
│   └── 卷2.zip
└── 漫画4 （图片、文件夹、压缩包混合）
    ├── 1.jpg
    ├── 2.jpg
    ├── 卷1.zip
    ├── 卷2.zip
    └── 卷3
        └── 卷3-1
            ├── 1.jpg
            └── 2.jpg
        └── 卷3-2.zip
```

> 特别的，如果想要为漫画指定封面，可以用`cover`/`folder`作为图片的文件名，放在想要漫画文件夹（或压缩包）的根路径上
