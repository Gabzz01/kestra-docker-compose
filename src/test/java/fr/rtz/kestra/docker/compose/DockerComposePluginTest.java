package fr.rtz.kestra.docker.compose;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


/**
 * This test will only test the main task, this allow you to send any input
 * parameters to your task and test the returning behaviour easily.
 */
@KestraTest
class DockerComposePluginTest {
    @Inject
    private RunContextFactory runContextFactory;

    private final Map<String, Object> ctxProperties = Map.of("yaml", """
        services:
            test:
                image: nginx
        """);

    @Test
    void run() throws Exception {
        final String projectName = "docker-compose-kestra-test";
        // Create & start containers
        RunContext upCtx = runContextFactory.of(ctxProperties);
        Up upTask = Up.builder()
            .yaml(new Property<>("{{ yaml }}"))
            .detached(new Property<>("true"))
            .projectName(new Property<>(projectName))
            .build();
        ScriptOutput upRunOutput = upTask.run(upCtx);
        assertThat(upRunOutput.getExitCode(), is(0));

        // Stop containers
        RunContext stopCtx = runContextFactory.of(ctxProperties);
        Stop stopTask = Stop.builder()
            .projectName(new Property<>(projectName))
            .build();
        ScriptOutput stopRunOutput = stopTask.run(stopCtx);
        assertThat(stopRunOutput.getExitCode(), is(0));

        // Start containers
        RunContext startCtx = runContextFactory.of(ctxProperties);
        Start startTask = Start.builder()
            .projectName(new Property<>(projectName))
            .build();
        ScriptOutput startRunOutput = startTask.run(startCtx);
        assertThat(startRunOutput.getExitCode(), is(0));

        // Stop and remove containers
        RunContext downCtx = runContextFactory.of(ctxProperties);
        Down downTask = Down.builder()
            .projectName(new Property<>(projectName))
            .build();
        ScriptOutput downRunOutput = downTask.run(downCtx);
        assertThat(downRunOutput.getExitCode(), is(0));
    }
}
