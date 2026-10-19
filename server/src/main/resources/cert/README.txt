【微信支付商户私钥证书放置目录】
请将从微信支付商户平台 (pay.weixin.qq.com) -> 账户中心 -> API安全 -> 申请API证书 下载解压出来的：
apiclient_key.pem (商户私钥文件)
直接复制存放在当前目录下即可！

Spring Boot 会自动通过 classpath:cert/apiclient_key.pem 加载该证书进行微信商家转账到零钱与退款签名。
