package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.model.Run;

import hudson.model.labels.LabelAtom;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.test.acceptance.docker.DockerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
public class PipelineSmokeTest {

    @Rule
    public DockerRule<ElasticsearchContainer> esContainer = new DockerRule<ElasticsearchContainer>(ElasticsearchContainer.class);
    private ElasticsearchContainer container;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setup() throws Exception {
        container = esContainer.get();
        container.waitForInit(30000);
        container.configureJenkins(j);
    }

    @Test
    public void spotcheck_Default() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition("echo 'Hello'", true));
        Run build = j.buildAndAssertSuccess(project);
        // Eventual consistency
        //TODO(oleg_nenashev): Probably we need terminator entries in logs
        //to automate handling of such use-cases
        Thread.sleep(10000);
        j.assertLogContains("Hello", build);
    }

    @Test
    public void spotcheck_cycle() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition("" +
                "for (int i = 0; i<10; i++) {\n" +
                "  sleep 1\n" +
                "  echo \"count: ${i}\"\n" +
                "}", true));
        Run build = j.buildAndAssertSuccess(project);
        // Eventual consistency
        //TODO(oleg_nenashev): Probably we need terminator entries in logs
        //to automate handling of such use-cases
        Thread.sleep(1000);
        j.assertLogContains("count: 9", build);
    }

    @Test
    public void spotcheck_Agent() throws Exception {
        j.createOnlineSlave(new LabelAtom("foo"));

        WorkflowJob project = j.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition("node('foo') {" +
                "  sh 'whoami'" +
                "}", true));
        Run build = j.buildAndAssertSuccess(project);
        // Eventual consistency
        //TODO(oleg_nenashev): Probably we need terminator entries in logs
        //to automate handling of such use-cases
        Thread.sleep(1000);
        j.assertLogContains("whoami", build);
    }

}
