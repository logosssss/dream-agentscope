package com.zhu.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 管理进程入口。只扫 com.zhu.admin，不扫用户进程里的接入层和执行内核。
 */
@SpringBootApplication(scanBasePackages = "com.zhu.admin")
public class AdminApplication {

    /** 独立进程启动。不要把这个类交给用户进程的组件扫描。 */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
