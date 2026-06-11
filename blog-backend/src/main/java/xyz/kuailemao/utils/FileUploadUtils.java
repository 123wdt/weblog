package xyz.kuailemao.utils;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import xyz.kuailemao.constants.Const;
import xyz.kuailemao.enums.UploadEnum;
import xyz.kuailemao.exceptions.FileUploadException;

import java.io.InputStream;
import java.util.*;

/**
 * 七牛云文件上传工具类
 */
@Slf4j
@Component
public class FileUploadUtils {

    @Resource
    private UploadManager uploadManager;

    @Resource
    private Auth auth;

    @Value("${qiniu.bucket}")
    private String bucket;

    @Value("${qiniu.domain}")
    private String domain;

    /**
     * 上传文件
     */
    public String upload(UploadEnum uploadEnum, MultipartFile file) throws Exception {
        isCheck(uploadEnum, file);
        if (isFormatFile(file.getOriginalFilename(), uploadEnum.getFormat())) {
            String name = UUID.randomUUID().toString();
            String key = uploadEnum.getDir() + name + "." + getFileExtension(file.getOriginalFilename());
            String token = auth.uploadToken(bucket);
            try (InputStream stream = file.getInputStream()) {
                Response response = uploadManager.put(stream, key, token, null, null);
                if (response.isOK()) {
                    return domain + "/" + key;
                }
            }
            throw new FileUploadException("上传文件失败");
        }
        log.error("--------------------上传文件格式不正确--------------------");
        throw new FileUploadException("上传文件类型错误");
    }

    /**
     * 上传文件 -- 指定文件名
     */
    public String upload(UploadEnum uploadEnum, MultipartFile file, String fileName) throws Exception {
        isCheck(uploadEnum, file);
        if (isFormatFile(file.getOriginalFilename(), uploadEnum.getFormat())) {
            String key = uploadEnum.getDir() + fileName + "." + getFileExtension(file.getOriginalFilename());
            String token = auth.uploadToken(bucket);
            try (InputStream stream = file.getInputStream()) {
                Response response = uploadManager.put(stream, key, token, null, null);
                if (response.isOK()) {
                    return domain + "/" + key;
                }
            }
            throw new FileUploadException("上传文件失败");
        }
        log.error("--------------------上传文件格式不正确--------------------");
        throw new FileUploadException("上传文件类型错误");
    }

    /**
     * 上传文件 -- 指定动态存储文件夹 -- 指定文件名
     */
    public String upload(UploadEnum uploadEnum, MultipartFile file, String fileName, String dir) throws Exception {
        isCheck(uploadEnum, file);
        if (isFormatFile(file.getOriginalFilename(), uploadEnum.getFormat())) {
            String key = uploadEnum.getDir() + dir + "/" + fileName + "." + getFileExtension(file.getOriginalFilename());
            String token = auth.uploadToken(bucket);
            try (InputStream stream = file.getInputStream()) {
                Response response = uploadManager.put(stream, key, token, null, null);
                if (response.isOK()) {
                    return domain + "/" + key;
                }
            }
            throw new FileUploadException("上传文件失败");
        }
        log.error("--------------------上传文件格式不正确--------------------");
        throw new FileUploadException("上传文件类型错误");
    }

    /**
     * 文件上传合法校验
     */
    public void isCheck(UploadEnum uploadEnum, MultipartFile file) throws FileUploadException {
        if (file.isEmpty()) {
            throw new FileUploadException("上传文件为空");
        }
        if (verifyTheFileSize(file.getSize(), uploadEnum.getLimitSize())) {
            throw new FileUploadException("上传文件超过限制大小:" + uploadEnum.getLimitSize() + "MB");
        }
    }

    /**
     * 获取文件后缀
     */
    public String getFileExtension(String originalFilename) {
        if (originalFilename != null) {
            return originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }
        return null;
    }

    public Boolean verifyTheFileSize(Long fileSize, Double limitSize) {
        double formatFileSize = convertFileSizeToMB(fileSize);
        return formatFileSize >= limitSize;
    }

    /**
     * B 转 MB
     */
    public double convertFileSizeToMB(long sizeInBytes) {
        double sizeInMB = (double) sizeInBytes / (1024 * 1024);
        return Double.parseDouble(String.format("%.2f", sizeInMB));
    }

    /**
     * 获取目录下的所有文件名称
     */
    public List<String> listFiles(String dir) {
        BucketManager bucketManager = getBucketManager();
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        List<String> fileNames = new ArrayList<>();
        try {
            BucketManager.FileListIterator iterator = bucketManager.createFileListIterator(bucket, prefix, 1000, null);
            while (iterator.hasNext()) {
                com.qiniu.storage.model.FileInfo[] items = iterator.next();
                for (com.qiniu.storage.model.FileInfo item : items) {
                    fileNames.add(item.key);
                }
            }
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
        }
        return fileNames;
    }

    /**
     * 批量删除
     */
    public boolean deleteFiles(List<String> fileNames) {
        BucketManager bucketManager = getBucketManager();
        for (String key : fileNames) {
            try {
                bucketManager.delete(bucket, key);
            } catch (QiniuException e) {
                log.error("删除文件失败: {}", key, e);
                return false;
            }
        }
        return true;
    }

    /**
     * 单文件删除
     */
    public boolean deleteFile(String dir, String fileName) {
        BucketManager bucketManager = getBucketManager();
        String key = dir + fileName;
        try {
            bucketManager.delete(bucket, key);
            log.info("文件 {} 已成功从七牛云删除", key);
            return true;
        } catch (QiniuException e) {
            log.error("删除七牛云文件 {} 失败: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 文件格式校验
     */
    public boolean isFormatFile(String fileName, List<String> format) {
        for (String s : format) {
            if (fileName.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文件是否存在
     */
    public boolean isFileExist(String dir, String fileName) {
        BucketManager bucketManager = getBucketManager();
        String key = dir + fileName;
        try {
            bucketManager.stat(bucket, key);
            return true;
        } catch (QiniuException e) {
            return false;
        }
    }

    /**
     * 从完整路径中获取文件名
     */
    public String getFileName(String path) {
        return path.substring(path.lastIndexOf("/") + 1);
    }

    /**
     * 文件大小转换(KB)
     */
    public Double convertFileSizeToKB(Long fileSize) {
        return fileSize / 1024.0;
    }

    /**
     * 获取 BucketManager
     */
    private BucketManager getBucketManager() {
        Configuration cfg = new Configuration(Region.region0());
        return new BucketManager(auth, cfg);
    }
}
