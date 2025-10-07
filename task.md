## Docker It Yourself



*本题推荐有一定项目经验的同学完成。*

*请详细阅读所有提供的文档（尤其是规范）；读懂并理解文档就已经成功80%了。*

## 目标

实现一个符合 [Docker Registry HTTP API V2 规范](https://docs.docker.com/reference/api/registry/latest/) 的简化后端服务，并通过出题人的集成测试。

## 技术实现要求

程序可以以任意语言实现。无论如何编写，程序的运行逻辑需要满足 API 文档所给定的逻辑，并满足下列技术指标。

- 正确处理 Blob 分片上传功能。
- 对于所有上传内容，需要由 Registry 校验并维护完整性。
- 需要处理内部错误，防止程序意外退出。
- 每个接口需要至少实现 400 和 404 错误响应（如有）。
- 能够在 Linux 环境中运行，无需实现对其它操作系统的兼容。测试用系统为 Debian GNU/Linux 13。
- 正确处理所有 http 请求的 content-type.
  - Manifest 相关接口需要支持 `application/vnd.docker.distribution.manifest.v2+json` 和 `application/vnd.docker.distribution.manifest.list.v2+json` 两种 content-type. 两种类型的定义请结合文档、参考说明和网络搜索自行理解。

此外，编写过程中需要保持代码结构清晰，并添加必要的注释。

为简化实现，对 Authorization 功能不作要求。

## 参考说明

Registry 一共要维护三类实体的信息：blob, manifest 和 manifest list. 

Manifest 是一个镜像的元数据，与逻辑意义上的镜像为一一对应关系。

```JSON
{
    "schemaVersion": 2,
    "mediaType": "application/vnd.oci.image.manifest.v1+json",
    "config": {
        "mediaType": "application/vnd.oci.image.config.v1+json",
        "digest": "sha256:999ffdddc1528999603ade1613e0d336874d34448a74db8f981c6fae4db91ad7",
        "size": 451,
        "data": "eyJjb25maWciOnsiRW52IjpbIlBBVEg9L3Vzci9sb2NhbC9zYmluOi91c3IvbG9jYWwvYmluOi91c3Ivc2JpbjovdXNyL2Jpbjovc2JpbjovYmluIl0sIkVudHJ5cG9pbnQiOltdLCJDbWQiOlsiYmFzaCJdfSwiY3JlYXRlZCI6IjIwMjUtMDktMDhUMDA6MDA6MDBaIiwiaGlzdG9yeSI6W3siY3JlYXRlZCI6IjIwMjUtMDktMDhUMDA6MDA6MDBaIiwiY3JlYXRlZF9ieSI6IiMgZGViaWFuLnNoIC0tYXJjaCAnYW1kNjQnIG91dC8gJ3RyaXhpZScgJ0AxNzU3Mjg5NjAwJyIsImNvbW1lbnQiOiJkZWJ1ZXJyZW90eXBlIDAuMTYifV0sInJvb3RmcyI6eyJ0eXBlIjoibGF5ZXJzIiwiZGlmZl9pZHMiOlsic2hhMjU2OjE4NWUwNGRhOWQ5NDcxNDFmZDcwM2RiZjM2MzYxYmRjMmZmNzdjYzI3Y2JmNTAwZmI5ZjQ4ODFjYjVkZGJlOTUiXX0sIm9zIjoibGludXgiLCJhcmNoaXRlY3R1cmUiOiJhbWQ2NCJ9Cg=="
    },
    "layers": [
        {
            "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
            "digest": "sha256:15b1d8a5ff03aeb0f14c8d39a60a73ef22f656550bfa1bb90d1850f25a0ac0fa",
            "size": 49279531
        }
    ]
}
```

Manifest中包含镜像所有层的列表。config.data 字段是可选项，如果有，则镜像的 config 直接以 base64 编码后的形式提供；反之，客户端会根据 config.digest 的内容获取作为 blob 的 config.

将上面的 config.data 解 base64 后，即可得到以下内容：

```JSON
{
    "config": {
        "Env": [
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        ],
        "Entrypoint": [],
        "Cmd": [
            "bash"
        ]
    },
    "created": "2025-09-08T00:00:00Z",
    "history": [
        {
            "created": "2025-09-08T00:00:00Z",
            "created_by": "# debian.sh --arch 'amd64' out/ 'trixie' '@1757289600'",
            "comment": "debuerreotype 0.16"
        }
    ],
    "rootfs": {
        "type": "layers",
        "diff_ids": [
            "sha256:185e04da9d947141fd703dbf36361bdc2ff77cc27cbf500fb9f4881cb5ddbe95"
        ]
    },
    "os": "linux",
    "architecture": "amd64"
}
```

Manifest list 是一个 Tag 所对应所有 Manifest 的元数据列表。获取 manifest list 后，通过对应的 digest 值获取 manifest, 再根据 manifest 的内容下载所有层。

```JSON
{
    "manifests": [
        {
            "annotations": {
                "com.docker.official-images.bashbrew.arch": "amd64",
                "org.opencontainers.image.base.name": "scratch",
                "org.opencontainers.image.created": "2025-09-08T00:00:00Z",
                "org.opencontainers.image.revision": "875d8cd35082521d449942a5fc0769ea216a1b87",
                "org.opencontainers.image.source": "https:\/\/github.com\/debuerreotype\/docker-debian-artifacts.git",
                "org.opencontainers.image.url": "https:\/\/hub.docker.com\/_\/debian",
                "org.opencontainers.image.version": "trixie"
            },
            "digest": "sha256:56b68c54f22562e5931513fabfc38a23670faf16bbe82f2641d8a2c836ea30fc",
            "mediaType": "application\/vnd.oci.image.manifest.v1+json",
            "platform": {
                "architecture": "amd64",
                "os": "linux"
            },
            "size": 1021
        },
        {
            "annotations": {
                "com.docker.official-images.bashbrew.arch": "amd64",
                "vnd.docker.reference.digest": "sha256:56b68c54f22562e5931513fabfc38a23670faf16bbe82f2641d8a2c836ea30fc",
                "vnd.docker.reference.type": "attestation-manifest"
            },
            "digest": "sha256:7f171df723386df6c33d3273e71debd443192157f8b233bce7e3884e6702dcfa",
            "mediaType": "application\/vnd.oci.image.manifest.v1+json",
            "platform": {
                "architecture": "unknown",
                "os": "unknown"
            },
            "size": 562
        }
    ],
    "mediaType": "application\/vnd.oci.image.index.v1+json",
    "schemaVersion": 2
}
```

（为维持题面简洁，非amd64架构的内容已被略去）

Manifest list 的内容需要由 registry 自行维护。

### API 列表解读

*注：以下内容由笔者自行总结，目的是方便各位理解 API 和 Registry 的工作原理，如有与官方 API 文档不一致的以文档为准。*

文档涉及的 11 个 API 一共有 4 个路径：`/v2/{name}/manifests/{reference}`, `/v2/{name}/blobs/{digest}`, `/v2/{name}/blobs/uploads`和`/v2/{name}/blobs/uploads/{uuid}`.

#### Manifest 操作

`/v2/{name}/manifests/{reference}`提供对 repo 下镜像的 manifest 的操作集，包含新增、获取、检查存在性和删除四个操作。

对于不同种类的`reference`，这个操作会返回两种不同的内容。

- 提供一个 tag 时，registry 会返回该 tag 对应的 manifest-list.
- 提供一个 digest 时，则会直接返回给定镜像的 manifest.

对于下载操作，你需要判断给定的 reference 类型并提供对应的内容。

对于上传操作：

- 客户端只提供 manifest, 你需要根据内容自行维护 manifest list.
  - 处理 manifest-list 时可以忽略 annotations 字段不作要求。
- Registry 需要维护内容的完整性（各种意义上）。
  - 所有可供下载的内容都由 hash 标记，因此所有上传内容都需要进行校验。
  - 上传 manifest 即代表 image 可以被下载，此时只有所有逻辑前提条件都得到满足，上传操作才能成功。

#### Blob 操作

Blob 的内容通常包括两种，但 registry 并不关心内部类型。

两种内容都以 digest 作为唯一标识符。对于一个 Blob, 可执行的操作分为三种：上传、检测存在性和下载。

*请详细阅读 API 文档并理解细节。* 