package xyz.kuailemao.config;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 七牛云对象存储配置
 */
@org.springframework.context.annotation.Configuration
public class QiniuConfig {

    @Value("${qiniu.accessKey}")
    private String accessKey;

    @Value("${qiniu.secretKey}")
    private String secretKey;

    @Bean
    public Auth qiniuAuth() {
        return Auth.create(accessKey, secretKey);
    }

    @Bean
    public UploadManager qiniuUploadManager() {
        Configuration cfg = new Configuration(Region.region0());
        return new UploadManager(cfg);
    }
}
