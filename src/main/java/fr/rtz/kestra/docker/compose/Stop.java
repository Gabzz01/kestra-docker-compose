package fr.rtz.kestra.docker.compose;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
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
    title = "Stop containers in a Docker Compose stack",
    description = "Stops running containers without removing them. They can be started again with the Start task."
)
@Plugin(
    examples = {
        @Example(
            title = "Docker Compose",
            code = """
                id: stop-containers
                namespace: company.team
                tasks:
                    - id: stop-containers
                        type: fr.rtz.kestra.docker.compose.Stop
                        projectName: my-compose-project
                """
        )
    }
)
// TODO example avec remote docker host
public class Stop extends AbstractDockerCompose implements RunnableTask<ScriptOutput> {

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        final var taskRunner = Process.instance();

        final Map<String, String> env = new HashMap<>();
        this.appendDockerComposeEnv(runContext, env);

        final var cmds = this.buildCommands(runContext);
        runContext.logger().info("Running: {}", cmds);
        var cmd = new CommandsWrapper(runContext)
            .withEnv(env)
            .withCommands(cmds)
            .withTaskRunner(taskRunner);
        return cmd.run();
    }

    private Property<List<String>> buildCommands(RunContext ctx) throws IllegalVariableEvaluationException {
        var array = this.initCmd(ctx);
        array.add("stop");
        return Property.of(array);
    }
}
