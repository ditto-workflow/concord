package com.walmartlabs.concord.runtime.v2.sdk;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import org.immutables.value.Value;

import java.nio.file.Path;

public interface SecretService {

    String exportAsString(String orgName, String name, String password) throws Exception;

    KeyPair exportKeyAsFile(String orgName, String name, String password) throws Exception;

    UsernamePassword exportCredentials(String orgName, String name, String password) throws Exception;

    Path exportAsFile(String orgName, String name, String password) throws Exception;

    String decryptString(String s) throws Exception;

    String encryptString(String orgName, String projectName, String value) throws Exception;

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface KeyPair {

        long serialVersionUID = 1L;

        Path privateKey();

        Path publicKey();

        static ImmutableKeyPair.Builder builder() {
            return ImmutableKeyPair.builder();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface UsernamePassword {

        long serialVersionUID = 1L;

        String username();

        String password();

        static UsernamePassword of(String username, String password) {
            return ImmutableUsernamePassword.builder()
                    .username(username)
                    .password(password)
                    .build();
        }
    }
}
