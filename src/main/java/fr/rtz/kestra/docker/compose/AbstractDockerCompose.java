package fr.rtz.kestra.docker.compose;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractDockerCompose extends Task {

    private final static String DOCKER_HOST = "DOCKER_HOST";
    private final static String COMPOSE_STATUS_STDOUT = "COMPOSE_STATUS_STDOUT";

    @Schema(
        title = "Docker Host"
    )
    protected Property<String> dockerHost;

    @Schema(
        title = "Docker Compose project name",
        description = "The project name is used to group containers under a single name. It is used as a prefix for container names and networks created by Docker Compose.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    protected Property<String> projectName;

    protected void appendDockerComposeEnv(RunContext ctx, Map<String, String> env) throws IllegalVariableEvaluationException {
        // Override docker host if set
        final var dockerHost = ctx.render(this.dockerHost).as(String.class).orElse("");
        env.put(DOCKER_HOST, dockerHost);
        // Redirect docker-compose logs to stdout if not set
        if (!env.containsKey(COMPOSE_STATUS_STDOUT)) {
            env.put(COMPOSE_STATUS_STDOUT, "1");
        }
    }

    protected List<String> initCmd(RunContext ctx) throws IllegalVariableEvaluationException {
        final var projectName = ctx.render(this.projectName).as(String.class).orElseThrow();
        return new ArrayList<>() {{
            add("docker-compose");
            add("--project-name");
            add(projectName);
        }};
    }
}
