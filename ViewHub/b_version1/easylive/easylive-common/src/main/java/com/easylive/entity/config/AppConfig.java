package com.easylive.entity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

//这个类的作用就是把配置文件里的参数，注入到这个类里面。类似于pojo的作用。
@Configuration
public class AppConfig {
    //此处配置文件里的参数，注入到这个变量中。
    @Value("${project.folder:}")
    private String projectFolder;

    @Value("${admin.account:}")
    private String adminAccount;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${showFFmpegLog:true}")
    private Boolean showFFmpegLog;

    @Value("${es.host.port:127.0.0.1:9200}")
    private String esHostPort;

    @Value("${es.index.video.name:easy_live}")
    private String esIndexVideoName;

    /**
     * 获取项目文件夹路径
     * 确保返回的路径以 / 结尾，兼容 Windows 和 Linux
     * @return 项目文件夹路径（以 / 结尾）
     */
    public String getProjectFolder() {
        if (projectFolder == null || projectFolder.isEmpty()) {
            return "/data/";
        }
        // 统一使用正斜杠，Java File 类会自动处理跨平台
        String path = projectFolder.replace("\\", "/");
        // 确保路径以 / 结尾
        if (!path.endsWith("/")) {
            path += "/";
        }
        return path;
    }

    public String getAdminAccount() {
        return adminAccount;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public  Boolean getShowFFmpegLog() {
        return showFFmpegLog;
    }

    public String getEsHostPort() {
        return esHostPort;
    }

    public String getEsIndexVideoName() {
        return esIndexVideoName;
    }
}
