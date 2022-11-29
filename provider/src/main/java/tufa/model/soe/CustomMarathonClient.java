package tufa.model.soe;

import feign.Feign;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import mesosphere.client.common.ModelUtils;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.MarathonException;
import mesosphere.marathon.client.auth.TokenAuthRequestInterceptor;

import java.util.Arrays;

/**
 * Created by adrian on 08.06.2017.
 */
public class CustomMarathonClient extends MarathonClient {
    public CustomMarathonClient() {
    }

    public static CustomMarathon getInstance(String endpoint) {
        return getInstance(endpoint, (RequestInterceptor[])null);
    }

    public static CustomMarathon getInstance(String endpoint, RequestInterceptor... interceptors) {
        Feign.Builder b = Feign.builder().encoder(new GsonEncoder(ModelUtils.GSON)).decoder(new GsonDecoder(ModelUtils.GSON)).errorDecoder(new MarathonErrorDecoder());
        if(interceptors != null) {
            b.requestInterceptors(Arrays.asList(interceptors));
        }

        b.requestInterceptor(new MarathonHeadersInterceptor());
        return (CustomMarathon)b.target(CustomMarathon.class, endpoint);
    }

    public static CustomMarathon getInstanceWithBasicAuth(String endpoint, String username, String password) {
        return getInstance(endpoint, new BasicAuthRequestInterceptor(username, password));
    }

    public static CustomMarathon getInstanceWithTokenAuth(String endpoint, String token) {
        return getInstance(endpoint, new TokenAuthRequestInterceptor(token));
    }

    static class MarathonErrorDecoder implements ErrorDecoder {
        MarathonErrorDecoder() {
        }

        public Exception decode(String methodKey, Response response) {
            return new MarathonException(response.status(), response.reason());
        }
    }

    static class MarathonHeadersInterceptor implements RequestInterceptor {
        MarathonHeadersInterceptor() {
        }

        public void apply(RequestTemplate template) {
            template.header("Accept", "application/json");
            template.header("Content-Type", "application/json");
        }
    }
}
