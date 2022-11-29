package tufa.model.events.deployments;


import tufa.model.events.AbstractEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Adrian Fraisse
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
public class DeploymentFailedEvent extends AbstractEvent {
    private String id;
}
