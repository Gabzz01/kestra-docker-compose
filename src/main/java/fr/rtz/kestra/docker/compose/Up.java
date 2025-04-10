package fr.rtz.kestra.docker.compose;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
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
    title = "Create and start container in a Docker Compose stack",
    description = "Full description of this task"
)
@Plugin(
    examples = {
        @Example(
            title = "Create and start a Docker Compose stack with detached mode",
            code = """
                id: up-containers
                namespace: company.team
                tasks:
                  - id: deploy
                    type: fr.rtz.kestra.docker.compose.Up
                    detached: true
                    yaml: |
                        services:
                            web:
                                image: nginx
                                ports:
                                    - "8080:80"
                            db:
                                image: postgres
                """
        ),
        @Example(
            title = "Create and start a Docker Compose stack from file",
            code = """
                id: up-containers
                namespace: company.team
                tasks:
                  - id: deploy
                    type: fr.rtz.kestra.docker.compose.Up
                    detached: true
                    yaml: read('docker-compose.yaml')
                """
        ),
        @Example(
            title = "Create and start a Docker Compose stack leveraging compose environment variables interpolation",
            code = """
                id: up-containers
                namespace: company.team
                tasks:
                  - id: deploy
                    type: fr.rtz.kestra.docker.compose.Up
                    detached: true
                    env:
                        WEB_IMG: nginx
                    yaml: |
                        services:
                            web:
                                image: ${WEB_IMG}
                                ports:
                                    - "8080:80"
                            db:
                                image: postgres
                """
        ),
        @Example(
            title = "Create and start a Docker Compose stack leveraging compose config files",
            code = """
                id: up-containers
                namespace: company.team
                tasks:
                  - id: deploy
                    type: fr.rtz.kestra.docker.compose.Up
                    projectName: my-compose-project
                    detached: true
                    yaml: |
                        services:
                            web:
                                image: nginx
                                ports:
                                    - "8080:80"
                            db:
                                image: postgres

                    inputFiles:
                      - id: docker-compose.override.yaml
                        content: |
                            version: '3'
                            services:
                              web:
                                environment:
                                  - FOO=bar
                """
        )
    }
)

// TODO example avec remote docker host
// TODO explore namespace file usage -> Permettre la résolution des configurations de stack
public class Up extends AbstractDockerCompose implements RunnableTask<ScriptOutput>, InputFilesInterface {

    @Schema(
        title = "Docker Compose stack definition (YAML).",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
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

        final var yaml = runContext.render(this.yaml).as(String.class).orElseThrow();
        runContext.workingDir().createFile("docker-compose.yaml", yaml.getBytes(StandardCharsets.UTF_8));

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
        array.add("up");
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
