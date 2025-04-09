package fr.rtz.kestra.docker.compose;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.QueueException;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * This test will load all flow located in `src/test/resources/flows/`
 * and will run an in-memory runner to be able to test a full flow. There is also a
 * configuration file in `src/test/resources/application.yml` that is only for the full runner
 * test to configure in-memory runner.
 */
@KestraTest(startRunner = true)
class DockerComposeRunTest {
    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow("flows/docker-compose.yaml")
    void flow(Execution execution) throws TimeoutException, QueueException {
        assertThat(execution.getTaskRunList(), hasSize(4));
        //assertThat(((Map<String, Object>)execution.getTaskRunList().get(2).getOutputs().get("child")).get("value"), is("task-id"));
    }
}
