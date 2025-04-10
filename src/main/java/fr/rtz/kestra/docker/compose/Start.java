package fr.rtz.kestra.docker.compose;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.InputFilesInterface;
import io.kestra.core.models.tasks.RunnableTask;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Start existing containers in a Docker Compose stack",
    description = "Starts existing containers in a given project"
)
@Plugin(
    examples = {
        @Example(
            title = "Start and wait for container to terminate",
            code = """
                id: start-containers
                namespace: company.team
                tasks:
                    - id: down
                        type: fr.rtz.kestra.docker.compose.Start
                        projectName: my-compose-project
                """
        ),
        @Example(
            title = "Start containers in detached mode",
            code = """
                id: start-containers
                namespace: company.team
                tasks:
                    - id: down
                        type: fr.rtz.kestra.docker.compose.Start
                        detached: true
                        projectName: my-compose-project
                """
        )
    }
)

// TODO example avec remote docker host
public class Start extends AbstractDockerCompose implements RunnableTask<ScriptOutput>, InputFilesInterface {

    @Schema(
        title = "Docker Compose stack definition (YAML)."
    )
    protected Property<String> yaml;

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

        final Map<String, String> env = runContext.render(
            this.getEnv()).asMap(String.class, String.class).isEmpty() ?
            new HashMap<>() :
            runContext.render(this.getEnv()).asMap(String.class, String.class);
        this.appendDockerComposeEnv(runContext, env);

        final var cmds = this.buildCommands(runContext);
        runContext.logger().info("Running: {}", cmds);
        return new CommandsWrapper(runContext)
            .withEnv(env)
            .withInputFiles(this.inputFiles)
            .withCommands(cmds)
            .withTaskRunner(taskRunner)
            .run();
    }

    private Property<List<String>> buildCommands(RunContext ctx) throws IllegalVariableEvaluationException {
        var array = this.initCmd(ctx);
        array.add("start");
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
