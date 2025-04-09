package fr.rtz.kestra.docker.compose;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
@Schema(
    title = "Trigger an execution randomly",
    description = "Trigger an execution randomly"
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    protected Property<Double> min = Property.of(0.5);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws IllegalVariableEvaluationException {
        RunContext runContext = conditionContext.getRunContext();
// TODO use docker compose ps -a and return a list of containers with status etc ...
        // Utiliser --format=json
        double random = Math.random();
        if (random < runContext.render(this.min).as(Double.class).orElseThrow()) {
            return Optional.empty();
        }

        runContext.logger().info("Will create an execution");
        Execution execution = TriggerService.generateExecution(
            this,
            conditionContext,
            context,
            Output.builder().build()
        );

        return Optional.of(execution);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private List<Output.ContainerInfo> containers;

        public record ContainerInfo(
            @JsonProperty("Service")
            String service,
            @JsonProperty("Name")
            String name,
            @JsonProperty("Command")
            String command,
            @JsonProperty("State")
            String state,
            @JsonProperty("Health")
            String health,
            @JsonProperty("ExitCode")
            Integer exitCode
        ) {
        }
    }
}
