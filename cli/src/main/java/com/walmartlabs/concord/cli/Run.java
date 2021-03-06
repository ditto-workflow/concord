package com.walmartlabs.concord.cli;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.cli.runner.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.imports.ImportManagerFactory;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.Runner;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(name = "run", description = "Run the current directory as a Concord process")
public class Run implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display the command's help message")
    boolean helpRequested = false;

    @Option(names = {"-e", "--extra-vars"}, description = "additional process variables")
    Map<String, String> extraVars = new LinkedHashMap<>();

    @Option(names = {"--deps-cache-dir"}, description = "process dependencies cache dir")
    Path depsCacheDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("depsCache");

    @Option(names = {"--repo-cache-dir"}, description = "repository cache dir")
    Path repoCacheDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("repoCache");

    @Option(names = {"--secret-dir"}, description = "secret store dir")
    Path secretStoreDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("secrets");

    @Option(names = {"--vault-dir"}, description = "vault dir")
    Path vaultDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("vaults");

    @Option(names = {"--vault-id"}, description = "vault id")
    String vaultId = "default";

    @Option(names = {"--imports-source"}, description = "default imports source")
    String importsSource = "https://github.com";

    @Option(names = {"--entry-point"}, description = "entry point")
    String entryPoint = Constants.Request.DEFAULT_ENTRY_POINT_NAME;

    @Option(names = {"-v", "--verbose"}, description = "verbose output")
    boolean verbose = false;

    @Parameters(arity = "0..1")
    Path targetDir = Paths.get(System.getProperty("user.dir"));

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("Not a directory: " + targetDir);
        }

        DependencyManager dependencyManager = new DependencyManager(depsCacheDir);

        ProjectLoaderV2.Result loadResult;
        try {
            Path exportDir = targetDir.resolve("target");

            new ProjectLoaderV2(new ImportManagerFactory(dependencyManager, new CliRepositoryExporter(repoCacheDir)).create())
                    .export(targetDir, exportDir, new CliImportsNormalizer(importsSource, verbose), StandardCopyOption.REPLACE_EXISTING);

            loadResult = new ProjectLoaderV2(new NoopImportManager())
                    .load(exportDir, new NoopImportsNormalizer());
        } catch (Exception e) {
            System.err.println("Project load error: " + e.getMessage());
            return -1;
        }

        ProcessDefinition processDefinition = loadResult.getProjectDefinition();

        UUID instanceId = UUID.randomUUID();

        if (verbose && !extraVars.isEmpty()) {
            System.out.println("Additional variables: " + extraVars);
        }

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .dependencies(new DependencyResolver(dependencyManager, verbose).resolveDeps(processDefinition))
                .build();

        ProcessConfiguration cfg = ProcessConfiguration.builder().from(processDefinition.configuration())
                .entryPoint(entryPoint)
                .instanceId(instanceId)
                .build();

        Injector injector = new InjectorFactory(new WorkingDirectory(targetDir),
                runnerCfg,
                () -> cfg,
                new ProcessDependenciesModule(targetDir, runnerCfg.dependencies()),
                new CliServicesModule(secretStoreDir, targetDir, new VaultProvider(vaultDir, vaultId)))
                .create();

        Runner runner = new Runner.Builder()
                .injector(injector)
                .workDir(targetDir)
                .processStatusCallback(id -> {
                })
                .build();

        Map<String, Object> args = new LinkedHashMap<>(cfg.arguments());
        args.putAll(extraVars);
        args.put(Constants.Context.TX_ID_KEY, instanceId.toString());
        args.put(Constants.Context.WORK_DIR_KEY, targetDir.toAbsolutePath().toString());

        System.out.println("Starting...");

        try {
            runner.start(processDefinition, cfg.entryPoint(), args);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error: " + e.getMessage());
            return 1;
        } finally {
            StateManager.cleanupState(targetDir);
            IOUtils.deleteRecursively(targetDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));
            IOUtils.deleteRecursively(targetDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME));
        }

        System.out.println("...done!");

        return 0;
    }
}
