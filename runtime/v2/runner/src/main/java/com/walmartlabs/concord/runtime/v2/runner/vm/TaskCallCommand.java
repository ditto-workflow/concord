package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.model.TaskCall;
import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.context.TaskContextImpl;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.logging.SegmentedLogger;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.CallContext;
import static com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallInterceptor.Method;

/**
 * Calls the specified task. Responsible for preparing the task's input
 * and processing the output.
 */
public class TaskCallCommand extends StepCommand<TaskCall> {

    private static final long serialVersionUID = 1L;

    public TaskCallCommand(TaskCall step) {
        super(step);
    }

    @Override
    protected void execute(Runtime runtime, State state, ThreadId threadId) {
        Frame frame = state.peekFrame(threadId);
        frame.pop();

        TaskProviders taskProviders = runtime.getService(TaskProviders.class);
        ContextFactory contextFactory = runtime.getService(ContextFactory.class);
        ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

        Context ctx = contextFactory.create(runtime, state, threadId, getStep(), UUID.randomUUID());

        TaskCall call = getStep();
        String taskName = call.getName();
        Task t = taskProviders.createTask(ctx, taskName);
        if (t == null) {
            throw new IllegalStateException("Task not found: " + taskName);
        }

        TaskCallOptions opts = call.getOptions();
        Map<String, Object> input = VMUtils.prepareInput(expressionEvaluator, ctx, opts.input());

        TaskCallInterceptor interceptor = runtime.getService(TaskCallInterceptor.class);

        String segmentId = ctx.execution().correlationId().toString();
        TaskContext taskContext = new TaskContextImpl(ctx, taskName, input);
        CallContext callContext = CallContext.builder()
                .taskName(taskName)
                .correlationId(ctx.execution().correlationId())
                .currentStep(getStep())
                .processDefinition(ctx.execution().processDefinition())
                .build();

        Serializable result = SegmentedLogger.withLogSegment(taskName, segmentId,
                () -> interceptor.invoke(callContext, Method.of("execute", taskContext),
                        () -> t.execute(taskContext)));

        String out = opts.out();
        if (out != null) {
            frame.setLocal(out, result); // TODO a custom result structure
        }
    }
}
