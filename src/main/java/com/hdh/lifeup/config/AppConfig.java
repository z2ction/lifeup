package com.hdh.lifeup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AppPropertiesConfig class<br/>
 * 系统应用的常量属性配置
 * @author hdonghong
 * @since 2018/08/20
 */
@Data
@ConfigurationProperties(prefix = "app")
@Component
public class AppConfig {

    /** 系统应用域名 */
    private String appPath;

    /** 静态文件的CDN服务商域名 */
    private String cdnPath;

    /** mob短信验证接口 */
    private String mobApi;

    /** 应用appkey */
    private String appKey;
}
