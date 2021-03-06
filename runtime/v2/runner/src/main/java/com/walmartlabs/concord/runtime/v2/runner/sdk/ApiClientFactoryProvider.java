package com.walmartlabs.concord.runtime.v2.runner.sdk;

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

import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.runtime.common.client.ApiClientFactoryImpl;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.sdk.ApiConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Named
@Singleton
public class ApiClientFactoryProvider implements Provider<ApiClientFactory> {

    private final ApiConfiguration cfg;
    private final InstanceId instanceId;

    @Inject
    public ApiClientFactoryProvider(ApiConfiguration cfg, InstanceId instanceId) {
        this.cfg = cfg;
        this.instanceId = instanceId;
    }

    @Override
    public ApiClientFactory get() {
        try {
            return new ApiClientFactoryImpl(cfg, instanceId.getValue());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
