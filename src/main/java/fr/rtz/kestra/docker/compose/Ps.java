package fr.rtz.kestra.docker.compose;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.AbstractLogConsumer;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.utils.TruthUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Lists containers with status.",
    description = "Periodically poll for a Docker Compose project containers status"
)
@Plugin(
    examples = {
        @Example(
            title = "Poll for unhealthy / stopped containers and alert via slack",
            full = true,
            code = """
                id: alert-docker
                namespace: fr.rtz.markeat.devops

                tasks:
                    # Prebuild msg to avoid struggling with quotes escaping etc ...
                  - id: build-msg
                    type: "io.kestra.plugin.core.output.OutputValues"
                    values:
                      msg: "Containers stopped : {{ trigger.containers | jq('[.[] | select((.State != \\"running\\") and .State != \\"healthy\\") | .Name] | join(\\", \\")') }}"
                  - id: alert
                    type: io.kestra.plugin.notifications.slack.SlackIncomingWebhook
                    url: kv('slack-webhook')
                    payload: "{{ {'text': outputs['build-msg'].values.msg } | toJson }}"
                triggers:
                  - id: poll-docker
                    type: fr.rtz.kestra.docker.compose.Ps
                    projectName: my-compose-project
                    outputCondition: "{{ containers | jq('.[] | select((.State != \\"running\\") and .State != \\"healthy\\")') | length > 0 }}"
                """
        )
    }
)
// TODO example avec remote docker host
public class Ps extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Ps.Output> {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

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

    @Schema(
        title = "Condition on the list of containers to trigger a flow which can be any expression that evaluates to a boolean value.",
        description = """
            The condition will be evaluated after running `docker-compose ps -a`, it can use the list of containers itself to determine whether to start a flow or not.
            The `containers` variable is provided when evaluating the condition.
            Boolean coercion allows 0, -0, null and '' to evaluate to false, all other values will evaluate to true.
            The condition will be evaluated before any 'generic trigger conditions' that can be configured via the `conditions` property.
            """
    )
    private Property<String> outputCondition;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        final var dockerHost = runContext.render(this.dockerHost).as(String.class).orElse("");
        final var projectName = runContext.render(this.projectName).as(String.class).orElseThrow();

        final var env = new HashMap<String, String>() {{
            put(DOCKER_HOST, dockerHost);
            put(COMPOSE_STATUS_STDOUT, "1");
        }};
        val cmdParts = new ArrayList<String>() {{
            add("docker-compose");
            add("--project-name");
            add(projectName);
            add("ps");
            add("-a");
            add("--format=json");
        }};
        runContext.logger().info("Running: {}", cmdParts);
        final var logsConsumer = new DockerComposeLogConsumer();
        final var taskRunner = Process.instance();
        final var response = new CommandsWrapper(runContext)
            .withEnv(env)
            .withCommands(Property.of(cmdParts))
            .withTaskRunner(taskRunner)
            .withLogConsumer(logsConsumer)
            .run();
        if (response.getExitCode() != 0) {
            runContext.logger().error("An error occurred while running the command");
            return Optional.empty();
        }
        runContext.logger().info("Output : {}", logsConsumer.getLines());
        final var containerInfoList = new ArrayList<Output.ContainerInfo>();
        for (String line : logsConsumer.getLines()) {
            try {
                containerInfoList.add(new ObjectMapper().readValue(line, Output.ContainerInfo.class));
            } catch (JsonProcessingException e) {
                runContext.logger().error("An error occurred while parsing JSON output from docker-compose : {}", e.getMessage());
            }
        }
        Map<String, Object> responseVariables = Map.of("containers", containerInfoList);
        String renderedCondition = runContext.render(this.outputCondition).as(String.class, responseVariables).orElse(null);
        if (TruthUtils.isTruthy(renderedCondition)) {
            runContext.logger().info("Condition evaluated to true, triggering flow.");
            Execution execution = TriggerService.generateExecution(
                this,
                conditionContext,
                context,
                Output.builder().containers(containerInfoList).build()
            );
            return Optional.of(execution);
        }
        runContext.logger().info("Condition evaluated to false, not triggering flow.");
        return Optional.empty();
    }

    @Getter
    private static class DockerComposeLogConsumer extends AbstractLogConsumer {

        private final Collection<String> lines = new ArrayList<>();

        @Override
        public void accept(String line, Boolean isStdErr, Instant instant) {
            this.accept(line, isStdErr);
        }

        @Override
        public void accept(String line, Boolean isStdErr) {
            if (isStdErr) {
                stdErrCount.getAndIncrement();
            } else {
                stdOutCount.getAndIncrement();
            }
            this.lines.add(line);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private List<Output.ContainerInfo> containers;

        @Getter
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ContainerInfo {
            @JsonProperty("ID")
            @Schema(
                title = "Id.",
                description = "The container Id."
            )
            private final String id;

            @JsonProperty("Service")
            @Schema(
                title = "Service.",
                description = "The service to which this container is an instance of."
            )
            private final String service;

            @JsonProperty("Name")
            @Schema(
                title = "Name.",
                description = "The container name."
            )
            private final String name;
            @JsonProperty("Command")
            private final String command;
            @JsonProperty("State")
            private final String state;
            @JsonProperty("Health")
            private final String health;
            @JsonProperty("ExitCode")
            private final Integer exitCode;
        }
    }
}
