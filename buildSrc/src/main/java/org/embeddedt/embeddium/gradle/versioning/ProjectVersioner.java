package org.embeddedt.embeddium.gradle.versioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ProjectVersioner {
    public static String computeVersion(File projectDir, Map<String, ?> projectProperties) {
        String modVersion = projectProperties.get("mod_version").toString();
        String minecraftVersion = projectProperties.get("minecraft_version").toString();
        boolean isReleaseBuild = projectProperties.containsKey("build.release");
        if (isReleaseBuild) {
            return "%s+mc%s".formatted(modVersion, minecraftVersion);
        } else {
            boolean isDirty;
            int betaVersion;
            try {
                if (new File(projectDir, ".git").isFile()) {
                    // JGit 6 does not resolve a linked worktree's shared object and ref directories.
                    String base = projectProperties.get("base_mc_version_commit").toString();
                    betaVersion = Integer.parseInt(gitOutput(projectDir, "rev-list", "--count", base + "..HEAD").trim());
                    isDirty = !gitOutput(projectDir, "status", "--porcelain").isBlank();
                } else {
                FileRepositoryBuilder builder = new FileRepositoryBuilder();
                try(Repository repository = builder.readEnvironment().findGitDir(projectDir).build()) {
                    try(RevWalk walk = new RevWalk(repository)) {
                        var baseCommit = walk.parseCommit(ObjectId.fromString(projectProperties.get("base_mc_version_commit").toString()));
                        var git = new Git(repository);
                        var headCommit = git.log().setMaxCount(1).call().iterator().next();
                        var iterator = git.log().addRange(baseCommit, headCommit).call().iterator();
                        isDirty = !git.status().call().isClean();
                        int count = 0;
                        while(iterator.hasNext()) {
                            count++;
                            iterator.next();
                        }
                        betaVersion = count;
                    }
                }
                }
            } catch(IOException | GitAPIException e) {
                e.printStackTrace();
                isDirty = false;
                betaVersion = 9999;
            }

            if(betaVersion > 0 || isDirty) {
                String[] versionComponents = modVersion.split("\\.");
                if(versionComponents.length >= 3) {
                    // Increase patch version
                    try {
                        versionComponents[2] = String.valueOf(Integer.parseInt(versionComponents[2]) + 1);
                        modVersion = String.join(".", versionComponents);
                    } catch(NumberFormatException ignored) {}
                }
            }

            return "%s-beta.%s%s+mc%s".formatted(modVersion, betaVersion, isDirty ? "-dirty" : "", minecraftVersion);
        }
    }

    private static String gitOutput(File directory, String... arguments) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(arguments));
        var process = new ProcessBuilder(command).directory(directory).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) throw new IOException("Git version query failed: " + output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git version query interrupted", e);
        }
        return output;
    }
}
