# HTTPS 证书目录

放置域名证书，供 `docker-compose.https.yml` 挂载到 Web 容器：

- `fullchain.pem`：完整证书链（服务器证书 + 中间证书）
- `privkey.pem`：私钥

```bash
sudo mkdir -p docker/ssl
sudo cp /path/to/fullchain.pem docker/ssl/fullchain.pem
sudo cp /path/to/privkey.pem docker/ssl/privkey.pem
sudo chmod 600 docker/ssl/*.pem
```

要求：

- 证书域名必须与 `.env` 中的 `WEB_BASE_URL`、`CORS_ALLOWED_ORIGINS`、`WX_PAY_NOTIFY_URL` 一致
- 证书未过期，且包含完整中间证书链
- 该目录下的证书文件已被 `.gitignore` 忽略，禁止提交
