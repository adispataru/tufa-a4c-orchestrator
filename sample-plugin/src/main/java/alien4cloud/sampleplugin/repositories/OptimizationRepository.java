package alien4cloud.sampleplugin.repositories;

import alien4cloud.dao.*;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.tufa.model.Optimization;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Map;

import static alien4cloud.dao.FilterUtil.singleKeyFilter;
import static alien4cloud.dao.FilterUtil.fromKeyValueCouples;

@Service
public class OptimizationRepository {
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;

    @PostConstruct
    public void initDAO(){
        ElasticSearchDAO dao = (ElasticSearchDAO) alienDAO;
        Map<String, String> esTypes = alienDAO.getTypesToIndices();
        if(!esTypes.containsKey(Optimization.class.getSimpleName())) {
            dao.initIndices("Optimization", null, Optimization.class);
        }
    }


    public void save(Optimization opti) {
        alienDAO.save(opti);
    }

    public void delete(Optimization opti) {
        alienDAO.delete(Optimization.class, opti.getId());
    }

    public Optimization findByApplicationNameAndApplicationVersion(String appName, String appVersion) {
        IESQueryBuilderHelper<Optimization> applicationName = alienDAO.buildQuery(Optimization.class)
                .setFilters(fromKeyValueCouples("applicationName", appName, "applicationVersion", appVersion));

        if (applicationName.count() > 0) {
            GetMultipleDataResult<Optimization> search = applicationName.prepareSearch().search(0, 100);
            for (Optimization opti : search.getData()) {
                if(opti.getApplicationName().equals(appName) && opti.getApplicationVersion().equals(appVersion))
                    return opti;
            }

        }
        return null;
    }

    public Optimization findById(String id) {
        IESQueryBuilderHelper<Optimization> applicationName = alienDAO.buildQuery(Optimization.class)
                .setFilters(fromKeyValueCouples("id", id));

        if (applicationName.count() > 0) {
            GetMultipleDataResult<Optimization> search = applicationName.prepareSearch().search(0, 100);
            for (Optimization opti : search.getData()) {
                if(opti.getId().equals(id))
                    return opti;
            }
        }
        return null;
    }
}
