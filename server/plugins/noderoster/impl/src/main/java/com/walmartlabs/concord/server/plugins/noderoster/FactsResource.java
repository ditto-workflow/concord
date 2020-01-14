package com.walmartlabs.concord.server.plugins.noderoster;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.server.plugins.noderoster.dao.HostsDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Named
@Singleton
@Path("/api/v1/noderoster/facts")
@Api(value = "Node Roster Facts", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
public class FactsResource implements Resource {

    private final HostManager hostManager;
    private final HostsDao hostsDao;

    @Inject
    public FactsResource(HostManager hostManager, HostsDao hostsDao) {
        this.hostManager = hostManager;
        this.hostsDao = hostsDao;
    }

    @GET
    @Path("/")
    @ApiOperation(value = "Get facts for a host")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFacts(@ApiParam @QueryParam("hostName") String hostName,
                             @ApiParam @QueryParam("hostId") UUID hostId) {

        UUID effectiveHostId = Utils.getHostId(hostManager, hostId, hostName);
        if (effectiveHostId == null) {
            return null;
        }

        // return the raw JSON string, no need to parse it just to serialize it back
        return Response.ok()
                .entity(hostsDao.getLastFacts(effectiveHostId))
                .build();
    }
}
