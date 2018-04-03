package com.cloudzone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * JenkinsServer
 *
 * @author zhoufei
 * @date 2018/3/12
 */
@EnableAutoConfiguration
@Configuration
@ComponentScan
@EnableEurekaClient
@EnableDiscoveryClient
@SpringBootApplication
public class JenkinsServer {
    public static void main(String[] args) {
        SpringApplication.run(JenkinsServer.class, args);
    }
}
