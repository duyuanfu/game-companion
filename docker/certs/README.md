# 微信支付商户证书目录

将商户 API 证书放在本目录，容器内路径为 `/opt/certs`：

- `apiclient_key.pem`
- `apiclient_cert.pem`

```bash
cp /path/to/apiclient_key.pem docker/certs/
cp /path/to/apiclient_cert.pem docker/certs/
chmod 600 docker/certs/*.pem
```

该目录下的 `.pem` / `.p12` / `.key` 已加入 `.gitignore`，禁止提交到仓库。
