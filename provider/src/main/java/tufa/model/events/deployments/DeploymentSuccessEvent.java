package tufa.model.events.deployments;


import tufa.model.events.AbstractEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @author Adrian Fraisse
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class DeploymentSuccessEvent extends AbstractEvent {
    private String id;
}
