package com.cloudzone.service;

import com.cloudzone.JenkinsServiceApi;
import com.cloudzone.common.entity.jenkins.JobConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class JenkinsJenkinsServiceApiImplTest {

    @Autowired
    private JenkinsServiceApi jenkinsServiceApi;
    private JobConfig jobConfig;

    @Before
    public void before() {
        jobConfig = new JobConfig();
        jobConfig.setJobName("test" + System.currentTimeMillis());
        jobConfig.setCodeBranch("master");
        jobConfig.setCodeUrl("http://10.112.101.94/cloudzone/autotest.git/");
        jobConfig.setBaseImage("10.112.101.90/cloudzone/alpine-sunjdk8:151");
        jobConfig.setImageUser("gc-cloud");
        jobConfig.setImageName("autotest:3.2");
        jobConfig.setRepositoryUrl("10.112.101.90");
        jobConfig.setRepositoryUsername("admin");
        jobConfig.setRepositoryPassword("Harbor12345");
        jobConfig.setRepositoryName("cloudzone");
        jobConfig.setJarName("autotest-1.0.jar");
    }

    @Test
    public void create() throws Exception {
        jenkinsServiceApi.createJenkinsJob(jobConfig);
    }
}
