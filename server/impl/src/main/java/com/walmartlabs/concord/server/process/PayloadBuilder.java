package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.MultipartUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.ValidationErrorsException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;

public final class PayloadBuilder {

    public static PayloadBuilder start(ProcessKey processKey) {
        return new PayloadBuilder(processKey);
    }

    public static PayloadBuilder start(PartialProcessKey processKey) {
        ProcessKey pk = new ProcessKey(processKey.getInstanceId(), new Timestamp(System.currentTimeMillis()));
        return new PayloadBuilder(pk);
    }

    public static PayloadBuilder resume(ProcessKey processKey) {
        return new PayloadBuilder(processKey);
    }

    public static PayloadBuilder basedOn(Payload payload) {
        return new PayloadBuilder(payload);
    }

    private Payload payload;

    private PayloadBuilder(Payload payload) {
        this.payload = payload;
    }

    private PayloadBuilder(ProcessKey processKey) {
        this.payload = new Payload(processKey);
    }

    public PayloadBuilder parentInstanceId(UUID parentInstanceId) {
        if (parentInstanceId != null) {
            payload = payload.putHeader(Payload.PARENT_INSTANCE_ID, parentInstanceId);
        }
        return this;
    }

    public PayloadBuilder kind(ProcessKind kind) {
        if (kind != null) {
            payload = payload.putHeader(Payload.PROCESS_KIND, kind);
        }
        return this;
    }

    public PayloadBuilder apply(Function<PayloadBuilder, PayloadBuilder> f) {
        return f.apply(this);
    }

    public PayloadBuilder with(MultipartInput input) throws IOException {
        Map<String, Path> attachments = payload.getAttachments();
        attachments = new HashMap<>(attachments != null ? attachments : Collections.emptyMap());

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        cfg = new HashMap<>(cfg != null ? cfg : Collections.emptyMap());

        ProcessKey pk = payload.getProcessKey();
        Path baseDir = ensureBaseDir();

        for (InputPart p : input.getParts()) {
            String name = MultipartUtils.extractName(p);
            if (name == null || name.startsWith("/") || name.contains("..")) {
                throw new ProcessException(pk, "Invalid attachment name: " + name, Response.Status.BAD_REQUEST);
            }

            if (p.getMediaType().isCompatible(MediaType.TEXT_PLAIN_TYPE)) {
                String v = p.getBodyAsString().trim();
                Map<String, Object> m = ConfigurationUtils.toNested(name, v);
                cfg = ConfigurationUtils.deepMerge(cfg, m);
            } else {
                Path dst = baseDir.resolve(name);
                Files.createDirectories(dst.getParent());
                try (InputStream in = p.getBody(InputStream.class, null);
                     OutputStream out = Files.newOutputStream(dst)) {
                    IOUtils.copy(in, out);
                }

                attachments.put(name, dst);
            }
        }

        payload = payload.putHeader(Payload.CONFIGURATION, cfg)
                .putAttachments(attachments);

        return this;
    }

    public PayloadBuilder workspace(Path workDir) {
        if (workDir != null) {
            payload = payload.putHeader(Payload.WORKSPACE_DIR, workDir);
        }
        return this;
    }

    public PayloadBuilder workspace(InputStream in) throws IOException {
        if (in == null) {
            return this;
        }

        Path baseDir = ensureBaseDir();

        Path archive = baseDir.resolve(Payload.WORKSPACE_ARCHIVE.name());
        Files.copy(in, archive);

        payload = payload.putAttachment(Payload.WORKSPACE_ARCHIVE, archive);

        return this;
    }

    public PayloadBuilder imports(Imports imports) {
        if (imports != null && !imports.isEmpty()) {
            payload = payload.putHeader(Payload.IMPORTS, imports);
        }
        return this;
    }

    public PayloadBuilder configuration(Map<String, Object> cfg) {
        if (cfg == null) {
            cfg = Collections.emptyMap();
        }

        Map<String, Object> prev = payload.getHeader(Payload.CONFIGURATION);
        if (prev != null) {
            cfg = ConfigurationUtils.deepMerge(prev, cfg);
        }

        payload = payload.putHeader(Payload.CONFIGURATION, cfg);
        return this;
    }

    public PayloadBuilder initiator(UUID initiatorId, String initiator) {
        if (initiatorId != null) {
            payload = payload.putHeader(Payload.INITIATOR_ID, initiatorId);
        }

        if (initiator != null) {
            payload = payload.putHeader(Payload.INITIATOR, initiator);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public PayloadBuilder meta(Map<String, Object> meta) {
        if (meta == null) {
            meta = Collections.emptyMap();
        }

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        Object v = cfg.getOrDefault(Constants.Request.META, Collections.emptyMap());
        if (!(v instanceof Map)) {
            throw new ValidationErrorsException("Expected a JSON object in '" + Constants.Request.META + "', got: " + v);
        }

        Map<String, Object> prev = (Map<String, Object>) v;
        meta = ConfigurationUtils.deepMerge(prev, meta);

        if (!meta.isEmpty()) {
            cfg.put(Constants.Request.META, meta);
        }

        payload = payload.putHeader(Payload.CONFIGURATION, cfg);

        return this;
    }

    public PayloadBuilder outExpressions(String[] out) {
        if (out != null && out.length != 0) {
            payload = payload.putHeader(Payload.OUT_EXPRESSIONS, new HashSet<>(Arrays.asList(out)));
        }
        return this;
    }

    public PayloadBuilder mergeOutExpressions(String[] out) {
        if (out == null || out.length == 0) {
            return this;
        }

        Set<String> s = payload.getHeader(Payload.OUT_EXPRESSIONS);
        s.addAll(Arrays.asList(out));
        payload = payload.putHeader(Payload.OUT_EXPRESSIONS, s);

        return this;
    }

    public PayloadBuilder organization(UUID orgId) {
        if (orgId != null) {
            payload = payload.putHeader(Payload.ORGANIZATION_ID, orgId);
        }
        return this;
    }

    public PayloadBuilder project(UUID projectId) {
        if (projectId != null) {
            payload = payload.putHeader(Payload.PROJECT_ID, projectId);
        }
        return this;
    }

    public PayloadBuilder repository(UUID repoId) {
        if (repoId != null) {
            payload = payload.putHeader(Payload.REPOSITORY_ID, repoId);
        }
        return this;
    }

    public PayloadBuilder entryPoint(String entryPoint) {
        if (entryPoint != null) {
            payload = payload.putHeader(Payload.ENTRY_POINT, entryPoint);
        }
        return this;
    }

    public PayloadBuilder eventName(String eventName) {
        payload = payload.putHeader(Payload.EVENT_NAME, eventName);
        return this;
    }

    public PayloadBuilder triggeredBy(TriggeredByEntry t) {
        payload = payload.putHeader(Payload.TRIGGERED_BY, t);
        return this;
    }

    public PayloadBuilder activeProfiles(Collection<String> activeProfiles) {
        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        cfg.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);

        payload = payload.putHeader(Payload.CONFIGURATION, cfg);
        return this;
    }

    public PayloadBuilder handlers(Set<String> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            return this;
        }

        payload = payload.putHeader(Payload.PROCESS_HANDLERS, handlers);
        return this;
    }

    public PayloadBuilder request(HttpServletRequest request) {
        if (request == null) {
            return this;
        }

        payload = payload.putHeader(Payload.SERVLET_REQUEST, request);
        return this;
    }

    private Path ensureBaseDir() throws IOException {
        Path baseDir = payload.getHeader(Payload.BASE_DIR);
        if (baseDir == null) {
            baseDir = IOUtils.createTempDir("payload");
            payload = payload.putHeader(Payload.BASE_DIR, baseDir);
        }
        return baseDir;
    }

    private void ensureWorkDir() throws IOException {
        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);
        if (workDir == null) {
            Path baseDir = ensureBaseDir();

            workDir = baseDir.resolve("workspace");
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
            }

            payload = payload.putHeader(Payload.WORKSPACE_DIR, workDir);
        }
    }

    public Payload build() throws IOException {
        ensureWorkDir();
        return payload;
    }
}
