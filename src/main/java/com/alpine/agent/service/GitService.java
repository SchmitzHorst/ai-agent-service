package com.alpine.agent.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    @Value("${template.repo-path}")
    private String templateRepoPath;

    @Value("${output.apps-path}")
    private String outputAppsPath;

    /**
     * Clone template repository to new app directory
     */
    public String cloneTemplate(String appName) throws GitAPIException {
        String targetPath = outputAppsPath + "/" + appName;

        log.info("Cloning template from {} to {}", templateRepoPath, targetPath);

        Git git = Git.cloneRepository()
                .setURI("file://" + templateRepoPath)
                .setDirectory(new File(targetPath))
                .call();

        git.close();

        log.info("Template cloned successfully");
        return targetPath;
    }

    /**
     * Write content to file in app directory
     */
    public void writeFile(String appPath, String relativePath, String content) throws IOException {
        Path filePath = Paths.get(appPath, relativePath);

        log.info("Writing file: {}", filePath);

        // Create parent directories if they don't exist
        Files.createDirectories(filePath.getParent());

        // Write content
        Files.writeString(filePath, content);

        log.info("File written successfully");
    }

    /**
     * Initialize new git repository and make initial commit
     */
    public void initGitRepo(String appPath, String appName) throws GitAPIException {
        log.info("Initializing git repository in {}", appPath);

        File repoDir = new File(appPath);

        // Remove .git from cloned template
        File oldGit = new File(appPath + "/.git");
        if (oldGit.exists()) {
            deleteDirectory(oldGit);
        }

        // Initialize new git repo
        Git git = Git.init()
                .setDirectory(repoDir)
                .call();

        // Add all files
        git.add()
                .addFilepattern(".")
                .call();

        // Initial commit
        git.commit()
                .setMessage("Initial commit: Generated " + appName)
                .call();

        log.info("Git repository initialized with initial commit");

        git.close();
    }

    /**
     * Commit changes
     */
    public void commit(String appPath, String message) throws GitAPIException, IOException {
        log.info("Committing changes: {}", message);

        Git git = Git.open(new File(appPath));

        git.add()
                .addFilepattern(".")
                .call();

        git.commit()
                .setMessage(message)
                .call();

        log.info("Changes committed");

        git.close();
    }

    /**
     * Push to remote repository
     */
    public void push(String appPath, String remoteUrl, String token) throws GitAPIException, IOException, URISyntaxException {
        log.info("Pushing to remote: {}", remoteUrl);

        Git git = Git.open(new File(appPath));

        // Add remote
        git.remoteAdd()
                .setName("origin")
                .setUri(new org.eclipse.jgit.transport.URIish(remoteUrl))
                .call();

        // Push with credentials
        git.push()
                .setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(token, "")
                )
                .call();

        log.info("Pushed to remote successfully");

        git.close();
    }

    /**
     * Helper method to delete directory recursively
     */
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}