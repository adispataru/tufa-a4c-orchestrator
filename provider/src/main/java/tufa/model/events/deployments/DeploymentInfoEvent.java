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
public class DeploymentInfoEvent extends AbstractEvent {

    private Plan plan;

    public class Plan {
        private String id;

        public Plan() {}

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
