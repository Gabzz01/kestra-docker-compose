package io.kestra.plugin.templates;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create and start a Docker Compose stack",
    description = "Full description of this task"
)
@Plugin(
    examples = {
        @io.kestra.core.models.annotations.Example(
            title = "Docker Compose",
            code = {"format: \"Text to be reverted\""}
        )
    }
)
// TODO exploire namespace file usage
public class Up extends AbstractDockerCompose implements RunnableTask<ScriptOutput>, InputFilesInterface {

    @Schema(
        title = "Docker Compose stack definition (YAML).",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    protected Property<String> stackDefinition;

    @Schema(
        title = "Enable detached mode.",
        description = "Run containers in the background"
    )
    protected Property<Boolean> detached;

    @Schema(
        title = "Force container creation.",
        description = "Recreate containers even if their configuration and image haven't changed"
    )
    protected Property<Boolean> forceRecreate;

    @Schema(
        title = "Wait for services to be up and running",
        description = "Wait for containers to be started before returning"
    )
    protected Property<Boolean> wait;

    @Schema(
        title = "Wait timeout",
        description = "Maximum duration in seconds to wait for the project to be running|healthy"
    )
    protected Property<Integer> waitTimeout;

    @Schema(
        title = "Additional environment variables to inject in the process"
    )
    private Property<Map<String, String>> env;

    private Object inputFiles;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        final var taskRunner = Process.instance();
        final var yaml = runContext.render(this.stackDefinition).as(String.class).orElseThrow();
        runContext.workingDir().createFile("docker-compose.yaml", yaml.getBytes(StandardCharsets.UTF_8));
        var cmd = new CommandsWrapper(runContext)
            .withEnv(runContext.render(
                this.getEnv()).asMap(String.class, String.class).isEmpty() ?
                new HashMap<>() :
                runContext.render(this.getEnv()).asMap(String.class, String.class)
            )
            .withInputFiles(this.inputFiles)
            .withCommands(this.buildCommands(runContext))
            .withTaskRunner(taskRunner)
            .withInputFiles(this.inputFiles);
        return cmd.run();
    }

    private Property<List<String>> buildCommands(RunContext ctx) throws IllegalVariableEvaluationException {
        final var dockerHost = ctx.render(this.dockerHost).as(String.class).orElse("");
        var array = new ArrayList<String>() {{
            if (!dockerHost.isBlank()) {
                add("DOCKER_HOST=" + dockerHost);
            }
            add("docker-compose");
            add("up");
        }};
        if (ctx.render(this.detached).as(Boolean.class).orElse(false)) {
            array.add("--detach");
        }
        if (ctx.render(this.forceRecreate).as(Boolean.class).orElse(false)) {
            array.add("--force-recreate");
        }
        if (ctx.render(this.wait).as(Boolean.class).orElse(false)) {
            array.add("--wait");
        }
        if (ctx.render(this.waitTimeout).as(Integer.class).orElse(0) > 0) {
            array.add("--wait-timeout");
            array.add(String.valueOf(ctx.render(this.waitTimeout).as(Integer.class).orElseThrow()));
        }
        return Property.of(array);
    }
}
