package com.snowcattle.game.common.http.advanced;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件控制器示例
 * 
 * 演示文件上传和列表功能
 */
public class FileController {
    
    // 文件上传目录
    private static final String UPLOAD_DIR = "game-core/src/test/resources/uploads";
    
    /**
     * GET /api/files - 获取文件列表
     */
    public String listFiles(HttpContext ctx) {
        try {
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            
            List<FileInfo> files = new ArrayList<>();
            File[] fileList = uploadDir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isFile()) {
                        files.add(new FileInfo(
                            file.getName(),
                            file.length(),
                            new java.util.Date(file.lastModified())
                        ));
                    }
                }
            }
            
            return JsonUtil.success(files);
            
        } catch (Exception e) {
            return JsonUtil.error(500, "获取文件列表失败: " + e.getMessage());
        }
    }
    
    /**
     * POST /api/files/upload - 上传文件
     * 
     * 注意：这是一个简化实现，实际文件上传需要使用 multipart/form-data
     * 这里仅作为示例演示
     */
    public String uploadFile(HttpContext ctx) {
        try {
            // 实际项目中应使用 multipart/form-data 解析
            // 这里仅返回提示信息
            return JsonUtil.error(501, "文件上传功能需要 multipart/form-data 支持，请参考 Netty 的 HttpPostRequestDecoder");
        } catch (Exception e) {
            return JsonUtil.error(500, "上传文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 文件信息
     */
    public static class FileInfo {
        public String name;
        public long size;
        public java.util.Date lastModified;
        
        public FileInfo(String name, long size, java.util.Date lastModified) {
            this.name = name;
            this.size = size;
            this.lastModified = lastModified;
        }
    }
}

