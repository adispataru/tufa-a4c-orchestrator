package alien4cloud.sampleplugin;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Plugin spring context configuration.
 */
//@EnableGlobalMethodSecurity(prePostEnabled = true)
//@Order(ManagementServerProperties.ACCESS_OVERRIDE_ORDER)
@Configuration
@ComponentScan("alien4cloud.sampleplugin")
public class ContextConfiguration {
    private static final String propertiesFile = "/conf/sde.conf";

    public static String callBackAddress;
    public static String cellManagerEndpoint;
    public static final String A4CEndpoint = "http://localhost:8088";
    public static final String brooklynEndpoint = "http://localhost:8081";
    public static boolean TELEMETRY = true;
    public static final String brooklynUser = "admin";
    public static final String brooklynPassword = "admin";
}
