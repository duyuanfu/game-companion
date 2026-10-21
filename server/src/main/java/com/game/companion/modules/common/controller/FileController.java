package com.game.companion.modules.common.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.game.companion.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "通用文件上传与多媒体网关", description = "用于陪练师原声音频、段位截图、战绩凭证真实持久化上传与多端播放")
public class FileController {

    private final String uploadDir = System.getProperty("user.dir") + File.separator + "uploads";

    private final com.game.companion.common.security.AuthGuard authGuard;

    @PostMapping("/upload")
    @Operation(summary = "上传音频或图片文件，返回全网可访问的真实HTTP路径")
    public CommonResult<String> uploadFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        // 上传属于写操作，必须登录
        authGuard.currentUserId();
        if (file.isEmpty()) {
            return CommonResult.failed("上传文件不能为空");
        }

        try {
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            } else {
                ext = ".mp3"; // 录音文件默认兜底扩展名
            }

            String newFileName = IdUtil.fastSimpleUUID() + ext;
            File dest = new File(dir, newFileName);
            file.transferTo(dest);

            // 动态根据请求主机拼出可访问的 URL
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = request.getServerPort();
            String fileUrl = scheme + "://" + serverName + ":" + serverPort + "/api/file/download/" + newFileName;

            log.info("【文件上传成功】原始文件名: {}, 保存路径: {}, 访问URL: {}", originalFilename, dest.getAbsolutePath(), fileUrl);
            return CommonResult.success(fileUrl, "文件上传成功");
        } catch (Exception e) {
            log.error("文件上传处理失败: ", e);
            return CommonResult.failed("文件上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileName}")
    @Operation(summary = "多媒体静态文件流式直读播放 (支持任意真机与微信播放)")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        // 【防目录穿越】仅允许访问 uploadDir 下的一层纯文件名
        String safeFileName = fileName;
        if (safeFileName == null || safeFileName.contains("/") || safeFileName.contains("\\")
                || safeFileName.contains("..")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        File file = new File(uploadDir, safeFileName);
        // 二次确认解析后的规范路径仍位于上传目录内
        try {
            String basePath = new File(uploadDir).getCanonicalPath();
            String targetPath = file.getCanonicalPath();
            if (!targetPath.startsWith(basePath + File.separator)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String mimeType = "audio/mpeg";
        if (safeFileName.endsWith(".png")) mimeType = "image/png";
        else if (safeFileName.endsWith(".jpg") || safeFileName.endsWith(".jpeg")) mimeType = "image/jpeg";
        else if (safeFileName.endsWith(".mp3")) mimeType = "audio/mpeg";
        else if (safeFileName.endsWith(".m4a")) mimeType = "audio/mp4";
        else if (safeFileName.endsWith(".wav")) mimeType = "audio/wav";
        else if (safeFileName.endsWith(".webp")) mimeType = "image/webp";

        response.setContentType(mimeType);
        response.setContentLengthLong(file.length());

        try (FileInputStream in = new FileInputStream(file);
             OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            log.warn("多媒体传输中断: {}", e.getMessage());
        }
    }
}
