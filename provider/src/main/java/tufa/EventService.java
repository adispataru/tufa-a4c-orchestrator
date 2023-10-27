package tufa;

import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.PaaSInstanceStateMonitorEvent;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EventService {

    TreeMap<Long, List<AbstractMonitorEvent>> events = new TreeMap<>();

    public void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventCallback){
        SortedMap<Long, List<AbstractMonitorEvent>> longListSortedMap = events.tailMap(date.getTime());
        if(longListSortedMap != null) {
            List<AbstractMonitorEvent> result = new ArrayList<>();

            longListSortedMap.values().forEach(result::addAll);
            eventCallback.onSuccess(result.toArray(new AbstractMonitorEvent[]{}));
        }

    }

    public void registerEvent(Date date, AbstractMonitorEvent ev){
        registerEvent(date.getTime(), ev);
    }

    public void registerEvent(long date, AbstractMonitorEvent ev){
        boolean found = false;
        for (List<AbstractMonitorEvent> value : events.values()) {
            for (AbstractMonitorEvent event : value) {

                if (equals(ev, event))
                    found = true;

            }
        }
        if(!found) {
            events.putIfAbsent(date, new ArrayList<>());
            events.get(date).add(ev);
        }
    }

    private boolean equals(AbstractMonitorEvent ev, AbstractMonitorEvent event) {
        if(event instanceof PaaSInstanceStateMonitorEvent && ev instanceof PaaSInstanceStateMonitorEvent){
            PaaSInstanceStateMonitorEvent pEvent = (PaaSInstanceStateMonitorEvent) event;
            PaaSInstanceStateMonitorEvent pEv = (PaaSInstanceStateMonitorEvent) ev;
            return pEvent.getOrchestratorId().equals(pEv.getOrchestratorId()) &&
                    pEvent.getDeploymentId().equals(pEv.getDeploymentId()) &&
                    pEvent.getInstanceId().equals(pEv.getInstanceId()) &&
                    pEvent.getInstanceState().equals(pEv.getInstanceState()) &&
                    pEvent.getNodeTemplateId().equals(pEv.getNodeTemplateId()) &&
                    pEvent.getAttributes().equals(pEv.getAttributes()) &&
                    pEvent.getRuntimeProperties().equals(pEv.getRuntimeProperties());

        }
        return false;
    }
}
