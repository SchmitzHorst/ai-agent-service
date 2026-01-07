package com.alpine.agent.service;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    @Value("${deployment.server.host}")
    private String serverHost;

    @Value("${deployment.server.user}")
    private String serverUser;

    @Value("${deployment.server.key-path}")
    private String sshKeyPath;

    @Value("${deployment.server.target-dir}")
    private String targetDir;  // /opt/apps

    /**
     * Deploy app to Hetzner server
     */
    public String deployApp(String appName, String appPath) {
        try {
            log.info("Starting deployment for: {}", appName);

            // 1. Connect via SSH
            Session session = createSSHSession();
            session.connect();

            log.info("SSH connected to {}", serverHost);

            // 2. Create target directory
            String remoteAppPath = targetDir + "/" + appName;
            executeCommand(session, "mkdir -p " + remoteAppPath);

            // 3. Copy files via SFTP
            copyFilesToServer(session, appPath, remoteAppPath);

            // 4. Create .env file
            createEnvFile(session, remoteAppPath, appName);

            // 5. Deploy with docker-compose
            deployWithDocker(session, remoteAppPath);

            // 6. Cleanup
            session.disconnect();

            String url = "https://" + appName + ".ai-alpine.ch";
            log.info("Deployment completed: {}", url);

            return "✅ Deployed successfully: " + url;

        } catch (Exception e) {
            log.error("Deployment failed", e);
            return "❌ Deployment failed: " + e.getMessage();
        }
    }

    /**
     * Create SSH session
     */
    private Session createSSHSession() throws JSchException {
        JSch jsch = new JSch();

        // JSch Debug Logger aktivieren
        JSch.setLogger(new com.jcraft.jsch.Logger() {
            public boolean isEnabled(int level) {
                return true;
            }

            public void log(int level, String message) {
                log.info("JSch [{}]: {}", level, message);
            }
        });

        log.info("Loading SSH key from: {}", sshKeyPath);

        try {
            jsch.addIdentity(sshKeyPath);
            log.info("SSH key loaded successfully");

            // Debug: Welche Identities hat JSch?
            java.util.Vector identities = jsch.getIdentityRepository().getIdentities();
            log.info("Number of identities loaded: {}", identities.size());
        } catch (Exception e) {
            log.error("Failed to load SSH key", e);
            throw new JSchException("Key load failed: " + e.getMessage());
        }

        Session session = jsch.getSession(serverUser, serverHost, 22);

        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "publickey");

        log.info("Connecting to {}@{}", serverUser, serverHost);

        return session;
    }

    /**
     * Execute command via SSH
     */
    private String executeCommand(Session session, String command) throws Exception {
        log.info("Executing: {}", command);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        channel.setOutputStream(outputStream);

        channel.connect();

        // Wait for completion
        while (!channel.isClosed()) {
            Thread.sleep(100);
        }

        String output = outputStream.toString();
        int exitCode = channel.getExitStatus();

        channel.disconnect();

        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + output);
        }

        log.info("Command output: {}", output);
        return output;
    }

    /**
     * Copy files to server via SFTP
     */
    private void copyFilesToServer(Session session, String localPath, String remotePath) throws Exception {
        log.info("Copying files from {} to {}", localPath, remotePath);

        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect();

        // Copy directory recursively
        uploadDirectory(sftpChannel, localPath, remotePath);

        sftpChannel.disconnect();

        log.info("Files copied successfully");
    }

    /**
     * Upload directory recursively
     */
    private void uploadDirectory(ChannelSftp sftp, String localDir, String remoteDir) throws Exception {
        File local = new File(localDir);

        if (!local.exists()) {
            throw new FileNotFoundException("Local directory not found: " + localDir);
        }

        // Create remote directory
        try {
            sftp.mkdir(remoteDir);
        } catch (SftpException e) {
            // Directory might already exist
        }

        for (File file : local.listFiles()) {
            if (file.isDirectory()) {
                // Skip .git and node_modules
                if (file.getName().equals(".git") || file.getName().equals("node_modules")) {
                    continue;
                }
                uploadDirectory(sftp, file.getAbsolutePath(), remoteDir + "/" + file.getName());
            } else {
                sftp.put(file.getAbsolutePath(), remoteDir + "/" + file.getName());
            }
        }
    }

    /**
     * Create .env file on server
     */
    private void createEnvFile(Session session, String remoteAppPath, String appName) throws Exception {
        log.info("Creating .env file");

        String envContent = String.format(
                "APP_NAME=%s\n" +
                        "DOMAIN=%s.ai-alpine.ch\n" +
                        "POSTGRES_DB=%sdb\n" +
                        "POSTGRES_USER=%suser\n" +
                        "POSTGRES_PASSWORD=%s\n" +
                        "SPRING_PROFILES_ACTIVE=prod\n" +
                        "SERVER_PORT=8080\n",
                appName,
                appName,
                appName.replace("-", ""),
                appName.replace("-", ""),
                generatePassword()
        );

        String command = String.format(
                "echo '%s' > %s/.env",
                envContent,
                remoteAppPath
        );

        executeCommand(session, command);
    }

    /**
     * Deploy with docker-compose
     */
    private void deployWithDocker(Session session, String remoteAppPath) throws Exception {
        log.info("Starting docker-compose deployment");

        // Build and start
        String command = String.format(
                "cd %s && docker compose -f docker-compose.prod.yml build && " +
                        "docker compose -f docker-compose.prod.yml up -d",
                remoteAppPath
        );

        executeCommand(session, command);

        // Wait for health checks
        Thread.sleep(30000);

        // Check status
        String statusCommand = String.format(
                "cd %s && docker compose -f docker-compose.prod.yml ps",
                remoteAppPath
        );

        String status = executeCommand(session, statusCommand);
        log.info("Container status:\n{}", status);
    }

    /**
     * Generate random password
     */
    private String generatePassword() {
        return "Pass" + System.currentTimeMillis();
    }
}