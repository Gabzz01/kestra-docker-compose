package fr.rtz.kestra.docker.compose;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
    title = "Stop and remove container for a Docker Compose stack",
    description = "Calls docker-compose down to stop and remove containers, networks, images, and volumes defined in the corresponding project, also remove images."
)
@Plugin(
    examples = {
        @io.kestra.core.models.annotations.Example(
            title = "Stop containers and remove images",
            code = """
                id: stop-n-remove-containers
                namespace: company.team
                tasks:
                    - id: down
                        type: fr.rtz.kestra.docker.compose.Down
                        projectName: my-compose-project
                        removeImages: all
                """
        )
    }
)
// TODO example avec remote docker host
public class Down extends AbstractDockerCompose implements RunnableTask<ScriptOutput> {

    @Schema(
        title = "Remove images.",
        allowableValues = "local, all",
        description = "Remove images used by services. \"local\" remove only images that don't have a custom tag (\"local\"|\"all\")"
    )
    protected Property<RemoveImagesOptions> removeImages;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        final var taskRunner = Process.instance();

        final Map<String, String> env = new HashMap<>();
        this.appendDockerComposeEnv(runContext, env);

        final var cmds = this.buildCommands(runContext);
        runContext.logger().info("Running: {}", cmds);
        return new CommandsWrapper(runContext)
            .withEnv(env)
            .withCommands(cmds)
            .withTaskRunner(taskRunner)
            .run();
    }

    private Property<List<String>> buildCommands(RunContext ctx) throws IllegalVariableEvaluationException {
        final var removeImages = ctx.render(this.removeImages).as(RemoveImagesOptions.class).orElse(null);
        final var array = this.initCmd(ctx);
        array.add("down");
        if (removeImages != null) {
            array.add("--rmi");
            array.add(removeImages.getValue());
        }
        return Property.of(array);
    }
}
