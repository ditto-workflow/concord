package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.Concord;
import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.ProcessEventEntry;
import com.walmartlabs.concord.client.ProcessEventsApi;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.*;

public class FlowEventsIT {

    @ClassRule
    public static final Concord concord = ConcordConfiguration.configure();

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        Payload payload = new Payload()
                .archive(FlowEventsIT.class.getResource("flowEvents").toURI());

        ConcordProcess proc = concord.processes().start(payload);

        ProcessEntry pe = proc.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        ProcessEventsApi processEventsApi = new ProcessEventsApi(concord.apiClient());
        List<ProcessEventEntry> events = processEventsApi.list(proc.instanceId(), "ELEMENT", null, null, null, null, null, null);
        assertNotNull(events);

        // ---
        // expression:
        // - ${log.info('BOO')}
        assertEvent(events, 0, new EventData()
                .location(9, 7, "concord.yml")
                .flow("default")
                .description("Expression: ${log.info('BOO')}"));

        // task full form:
        // - task: log
        //   in:
        //     msg: "test"
        // pre
        assertEvent(events, 1, new EventData()
                .pre()
                .correlationId()
                .location(12, 7, "concord.yml")
                .flow("default")
                .name("log")
                .description("Task: log"));
        // post
        assertEvent(events, 2, new EventData()
                .post()
                .duration()
                .correlationId()
                .location(12, 7, "concord.yml")
                .flow("default")
                .name("log")
                .description("Task: log"));

        // script:
        // - script: js
        assertEvent(events, 3, new EventData()
                .location(17, 7, "concord.yml")
                .flow("default")
                .description("Script: js"));

        // if
        // - if: ${1 == 1}
        assertEvent(events, 4, new EventData()
                .location(22, 7, "concord.yml")
                .flow("default")
                .description("Check: ${1 == 1}"));

        // - log: "It's true!"
        // pre
        assertEvent(events, 5, new EventData()
                .pre()
                .correlationId()
                .location(24, 11, "concord.yml")
                .flow("default")
                .name("log")
                .description("Task: log"));
        // post
        assertEvent(events, 6, new EventData()
                .post()
                .correlationId()
                .duration()
                .location(24, 11, "concord.yml")
                .flow("default")
                .name("log")
                .description("Task: log"));

        // - switch: ${myVar}
        assertEvent(events, 7, new EventData()
                .location(26, 7, "concord.yml")
                .flow("default")
                .description("Switch: ${myVar}"));

        // - log: "It's red!"
        // pre
        assertEvent(events, 8, new EventData()
                .pre()
                .correlationId()
                .location(28, 11, "concord.yml")
                .flow("default")
                .name("log")
                .description("Task: log"));
        // post
        assertEvent(events, 9, new EventData()
                .post()
                .correlationId()
                .duration()
                .location(28, 11, "concord.yml")
                .flow("default")
                .name("log")
                .description("Task: log"));

        // set variables
        assertEvent(events, 10, new EventData()
                .location(30, 7, "concord.yml")
                .flow("default")
                .description("Set variables"));

        // flow call
        assertEvent(events, 11, new EventData()
                .location(33, 7, "concord.yml")
                .flow("default")
                .description("Flow call: returnFlow"));

        // return
        assertEvent(events, 12, new EventData()
                .location(38, 7, "concord.yml")
                .flow("returnFlow")
                .description("Return"));

        // flow call
        assertEvent(events, 13, new EventData()
                .location(35, 7, "concord.yml")
                .flow("default")
                .description("Flow call: exitFlow"));

        // exit
        assertEvent(events, 14, new EventData()
                .location(41, 7, "concord.yml")
                .flow("exitFlow")
                .description("Exit"));
    }

    private static void assertEvent(List<ProcessEventEntry> events, int index, EventData expected) {
        assertTrue(index < events.size());

        Set<String> toIntKeys = new HashSet<>(Arrays.asList("line", "column"));
        Map<String, Object> actual = events.get(index).getData().entrySet().stream()
            .map(e -> {
                if (toIntKeys.contains(e.getKey())) {
                    return new AbstractMap.SimpleEntry<>(e.getKey(), ((Number)e.getValue()).intValue());
                }
                return e;
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertEquals(expected, actual);
    }

    static class EventData extends HashMap<String, Object> {

        public EventData location(int line, int column, String fileName) {
            put("line", line);
            put("column", column);
            put("fileName", fileName);
            return this;
        }

        public EventData flow(String flow) {
            put("processDefinitionId", flow);
            return this;
        }

        public EventData description(String description) {
            put("description", description);
            return this;
        }

        public EventData pre() {
            put("phase", "pre");
            return this;
        }

        public EventData post() {
            put("phase", "post");
            return this;
        }

        public EventData name(String name) {
            put("name", name);
            return this;
        }

        public EventData correlationId() {
            put("correlationId", new Object() {
                @Override
                public boolean equals(Object obj) {
                    return true;
                }
            });
            return this;
        }

        public EventData duration() {
            put("duration", new Object() {
                @Override
                public boolean equals(Object obj) {
                    return true;
                }
            });
            return this;
        }
    }
}
