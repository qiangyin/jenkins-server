package com.cloudzone.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.cloudzone.JenkinsServiceApi;
import com.cloudzone.common.entity.ResponseResult;
import com.cloudzone.common.entity.jenkins.JenkinsJob;
import com.cloudzone.common.entity.jenkins.JobConfig;
import com.cloudzone.common.entity.jenkins.JobDetail;
import com.cloudzone.common.entity.jenkins.JobStatus;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

/**
 * JenkinsServiceApiImpl
 *
 * @author zhoufei
 * @date 2018/3/13
 */
@Service
public class JenkinsServiceApiImpl implements JenkinsServiceApi {
    private static final Logger logger = LoggerFactory.getLogger(JenkinsServiceApiImpl.class);

    @Value("${jenkins.host}")
    private String host;
    @Value("${jenkins.port}")
    private int port;
    @Value("${jenkins.username}")
    private String username;
    @Value("${jenkins.password}")
    private String password;

    @Value("${job.config.path}")
    private String jobConfigPath;

    @Value("${console.server.url}")
    private String consoleURL;

    @Override
    public ResponseResult<JenkinsJob> createJenkinsJob(@RequestBody JobConfig jobConfig) {
        ResponseResult<JenkinsJob> responseResult = new ResponseResult<>();

        try {
            JenkinsServer jenkinsServer = buildJenkinsServer();
            Map<String, Job> jobs = jenkinsServer.getJobs();

            if (jobs.containsKey(jobConfig.getJobName())) {
                throw new Exception(String.format("任务名称已经存在：%s", jobConfig.getJobName()));
            }

            VelocityContext context = buildJobXmlConfigData(jobConfig);
            jenkinsServer.createJob(jobConfig.getJobName(), buildJobXmlConfig(context, jobConfigPath));

            JenkinsJob job = new JenkinsJob();
            job.setStatus(JobStatus.NOT_BUILT);
            job.setName(jobConfig.getJobName());
            job.setJobConfig(jobConfig);

            responseResult.setData(job);
            responseResult.setCode(HttpStatus.OK.value());
            responseResult.setMsg(HttpStatus.OK.toString());
        } catch (Exception e) {
            logger.error("创建任务失败：Jenkins服务器异常：{}", e);
            responseResult.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            responseResult.setMsg("Jenkins服务器异常：" + e.getLocalizedMessage());
        }

        return responseResult;
    }

    @Override
    public boolean deleteJob(@RequestParam("jobName") String jobName) throws Exception {
        try {
            buildJenkinsServer().deleteJob(jobName);
        } catch (IOException e) {
            logger.error("删除任务失败，Jenkins服务器异常：{}", e);
            throw new Exception(String.format("删除任务失败，Jenkins服务器异常：%s", e.getLocalizedMessage()), e);
        }

        return true;
    }

    @Override
    public ResponseResult<Integer> buildJob(@RequestParam("jobName") String jobName) {
        ResponseResult<Integer> responseResult = new ResponseResult<>();

        try {
            Job job = getJob(jobName);
            job.build();
            JobWithDetails jobWithDetails = job.details();
            int buildNumber = jobWithDetails.getLastBuild().getNumber();

            if (buildNumber == -1) {
                buildNumber = 1;
            }

            responseResult.setData(buildNumber);
            responseResult.setCode(HttpStatus.OK.value());
            responseResult.setMsg(HttpStatus.OK.toString());
            logger.info(String.format("开始构建任务，任务名称：%s，构建编号：%d", jobName, buildNumber));
        } catch (Exception e) {
            logger.error("构建任务失败，Jenkins服务器异常：", e);
            responseResult.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            responseResult.setMsg("Jenkins服务器异常：" + e.getLocalizedMessage());
        }

        return responseResult;
    }


    @Override
    public ResponseResult<JobDetail> jobDetails(@RequestParam("jobName") String jobName) {
        ResponseResult<JobDetail> responseResult = new ResponseResult<>();
        try {
            getJob(jobName);
            RestTemplate restTemplate = createRestTemplate();
            String url = String.format("%sjob/%s/api/json?pretty=true", baseUrl(), jobName);
            String response = restTemplate.getForObject(url, String.class);
            JobDetail jobDetail = new JobDetail(JSONObject.parseObject(response));
            responseResult.setData(jobDetail);
            responseResult.setCode(HttpStatus.OK.value());
            responseResult.setMsg(HttpStatus.OK.toString());
        } catch (Exception e) {
            logger.error("构建任务详情失败，Jenkins服务器异常：{}", e);
            responseResult.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            responseResult.setMsg("Jenkins服务器异常：" + e.getLocalizedMessage());
        }
        return responseResult;
    }

    @Override
    public String jobBuildInfo(@RequestParam("jobName") String jobName, @RequestParam("buildNumber") Integer buildNumber) throws Exception {
        String response;
        try {
            getJob(jobName);
            RestTemplate restTemplate = createRestTemplate();
            String url = String.format("%sjob/%s/%d/consoleText", baseUrl(), jobName, buildNumber);
            response = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            logger.error("构建任务详情失败，Jenkins服务器异常：{}", e);
            throw new Exception(String.format("构建任务详情失败，Jenkins服务器异常：%s", e.getLocalizedMessage()), e);
        }

        return response;
    }

    private VelocityContext buildJobXmlConfigData(JobConfig jobConfig) {
        VelocityContext context = new VelocityContext();
        context.put("CodeBranch", jobConfig.getCodeBranch());
        context.put("CodeUrl", jobConfig.getCodeUrl());
        context.put("BaseImage", jobConfig.getBaseImage());
        context.put("ImageUser", jobConfig.getImageUser());
        context.put("ImageName", jobConfig.getImageName());
        context.put("RepositoryUrl", jobConfig.getRepositoryUrl());
        context.put("RepositoryUsername", jobConfig.getRepositoryUsername());
        context.put("RepositoryPassword", jobConfig.getRepositoryPassword());
        context.put("RepositoryName", jobConfig.getRepositoryName());
        context.put("JarName", jobConfig.getJarName());
        context.put("JarPath", jobConfig.getJarPath());
        context.put("ConsoleURL", consoleURL);
        context.put("ExposePort", jobConfig.getExposePort());

        return context;
    }

    private String buildJobXmlConfig(VelocityContext context, final String templatePath) {
        StringWriter configWriter = new StringWriter();
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loader", "class");
        velocityEngine.setProperty("class.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.init();

        Template template = velocityEngine.getTemplate(templatePath);
        template.merge(context, configWriter);

        return configWriter.toString();
    }

    protected JenkinsServer buildJenkinsServer() throws Exception {
        JenkinsServer jenkinsServer;

        try {
            URI serverURI = new URI(String.format("http://%s:%d)", host, port));
            jenkinsServer = new JenkinsServer(serverURI, username, password);
        } catch (URISyntaxException e) {
            logger.error("Jenkins服务URI错误：{}:{}", host, port);
            throw new Exception(String.format("Jenkins服务URI错误：%s:%d", host, port), e);
        }

        return jenkinsServer;
    }

    protected Job getJob(String jobName) throws Exception {
        Job job;

        try {
            JenkinsServer jenkinsServer = buildJenkinsServer();
            job = jenkinsServer.getJob(jobName);
            if (job == null) {
                logger.warn("任务不存在：{}", jobName);
                throw new Exception(String.format("任务不存在：%s", jobName));
            }
        } catch (IOException e) {
            logger.error("Jenkins服务URI错误：{}:{} {}", host, port, e);
            throw new Exception(String.format("Jenkins服务URI错误：%s:%d", host, port), e);
        }

        return job;
    }

    protected String baseUrl() {
        return "http://" + host + ":" + port + "/";
    }

    protected RestTemplate createRestTemplate() {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        RestTemplate template = builder.build();
        template.setMessageConverters(
                Arrays.asList(
                        new FormHttpMessageConverter(),
                        new StringHttpMessageConverter()
                )
        );

        template.getInterceptors().add(new BasicAuthorizationInterceptor(username, password));

        return template;
    }
}
