package tufa.model.soe;

import feign.Headers;
import feign.RequestLine;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonException;
import mesosphere.marathon.client.model.v2.Group;
import mesosphere.marathon.client.model.v2.Result;

/**
 * Created by adrian on 08.06.2017.
 */
public interface CustomMarathon extends Marathon{
    @RequestLine("PUT /v2/groups")
    @Headers({"X-API-Source: marathon/client"})
    Result updateGroup(Group var1) throws MarathonException;
}
