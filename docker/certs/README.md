# 微信支付商户证书目录

将商户 API 证书放在本目录，容器内路径为 `/opt/certs`：

- `apiclient_key.pem`
- `apiclient_cert.pem`

```bash
cp /path/to/apiclient_key.pem docker/certs/
cp /path/to/apiclient_cert.pem docker/certs/

# 容器内以 UID 10001 运行，必须让该用户可读
sudo chown 10001:10001 docker/certs/*.pem
chmod 600 docker/certs/*.pem

docker compose restart server
```

如果日志出现 `Permission denied` 读取证书，先检查宿主机证书属主与权限。

该目录下的 `.pem` / `.p12` / `.key` 已加入 `.gitignore`，禁止提交到仓库。
