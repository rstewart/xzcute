package com.shopwiki.xzcute.sshexec;

import java.io.*;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Charsets;
import com.jcraft.jsch.*;

public class SSH {

    public static final int CONNECT_TIMEOUT = (int)TimeUnit.MINUTES.toMillis(1);

    private static final UserInfo USER_INFO = new UserInfo() {

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {
            System.out.println("showMessage: " + message);
        }
    };

    public static class SSHException extends Exception {
        public SSHException(String message) {
            super(message);
        }

        public SSHException(String message, Exception e) {
            super(message, e);
        }
    }

    public static String sendCommand(String username, String privateKeyFile, String host, String command) throws SSHException {
        return sendCommand(username, privateKeyFile, host, command, false);
    }

    public static String sendCommand(String username, String privateKeyFile, String host, String command, boolean checkExitCode) throws SSHException {
        Session session = null;
        try {
            session = createSession(username, privateKeyFile, host);
            ChannelExec channel = (ChannelExec)session.openChannel("exec");

            // Gross hack to enable password-less sudo -Rob
            if (command.contains("sudo ")) {
                channel.setPty(true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            channel.setOutputStream(baos);
            channel.setErrStream(baos);

            channel.setCommand(command);
            channel.connect();

            while (! channel.isClosed()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            channel.disconnect();

            String output = new String(baos.toByteArray(), Charsets.UTF_8);

            if (checkExitCode && channel.getExitStatus() != 0) {
                throw new SSHException(output);
            }

            return output;
        } catch (JSchException e) {
            throw new SSHException("SSH Failed (" + host + ")\n" + command, e);
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private static Session createSession(String username, String privateKeyFile, String host) throws JSchException {
        JSch jsch = new JSch();

        if (privateKeyFile != null) {
            jsch.addIdentity(privateKeyFile);
        } else {
            findAndAddIdentities(jsch);
        }

        Session session = jsch.getSession(username, host, 22);
        session.setUserInfo(USER_INFO);
        session.setTimeout(CONNECT_TIMEOUT);
        session.connect(CONNECT_TIMEOUT);
        return session;
    }

    private static void findAndAddIdentities(JSch jsch) {
        String home = System.getProperty("user.home", "");
        File dir = new File(home + "/.ssh");

        if (! dir.exists()) {
            return;
        }

        for (String file : dir.list()) {
            if (file.equals("config") || file.equals("known_hosts") || file.equals("authorized_keys") || file.endsWith(".pub" )) {
                continue;
            }
            String path = dir.toString() + "/" + file;
            try {
                jsch.addIdentity(path);
            } catch (JSchException e) {
                System.err.println("Couldn't load SSH identity from file: " + path);
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] jargs) throws SSHException {
        System.out.println();

        Args args = new Args(jargs);
        String user = args.get("user");
        String key = args.get("key");
        String host = args.get("host");
        String cmd = args.get("cmd");
        boolean checkExitCode = args.hasFlag("check");

        String out = sendCommand(user, key, host, cmd, checkExitCode);
        System.out.println(out);
    }
}
