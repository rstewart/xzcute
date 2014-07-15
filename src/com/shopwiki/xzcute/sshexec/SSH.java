// SSH.java
package com.shopwiki.xzcute.sshexec;

import java.io.*;

import com.jcraft.jsch.*;
import com.shopwiki.util.*;

/**
 * @owner eliot
 * @owner avery
 */
public class SSH {

    public static final boolean DEBUG = false;
    public static final int CONNECT_TIMEOUT = (int)TimeConstants.MS_MINUTE;

    private static class MyUserInfo implements UserInfo {

        @Override
        public String getPassphrase(){
            if ( DEBUG ) System.out.println( "getPassphrase" );
            return null;
        }

        @Override
        public String getPassword(){
            if ( DEBUG ) System.out.println( "getPassword" );
            return null;
        }

        @Override
        public boolean promptPassphrase(String message){
            if ( DEBUG ) System.out.println( "promptPassphrase: " + message );
            return false;
        }

        @Override
        public boolean promptPassword(String message){
            if ( DEBUG ) System.out.println( "promptPassword: " + message );
            return false;
        }

        @Override
        public boolean promptYesNo(String message){
            if ( DEBUG ) System.out.println( "promptYesNo: " + message );
            return true;
        }

        @Override
        public void showMessage(String message){
            System.out.println( "showMessage: " + message );
        }
    }

    public static class SSHException extends Exception {
        public SSHException( String message ){
            super( message );
        }

        public SSHException( String message, Exception e ){
            super( message , e );
        }
    }

    public static String sendCommand( String host , String command ) throws SSHException {
        return sendCommand(host, command, false);
    }

    public static String sendCommand( String host , String command , boolean checkExitCode ) throws SSHException {
        return sendCommand("localadmin", null, host, command, checkExitCode);
    }

    public static String sendCommand( String username, String privateKeyFile, String host , String command ) throws SSHException {
        return sendCommand(username, privateKeyFile, host, command, false);
    }

    public static String sendCommand( String username, String privateKeyFile, String host , String command , boolean checkExitCode ) throws SSHException {
        Session session = null;
        try {
            session = createSession( username, privateKeyFile, host );
            ChannelExec channel = (ChannelExec)session.openChannel("exec");

            // Gross hack to enable password-less sudo for ec2-user -Rob
            if (username.equals("ec2-user") && command.contains("sudo ")) {
                channel.setPty(true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            channel.setOutputStream(baos);
            channel.setErrStream(baos);

            channel.setCommand( command );
            channel.connect();

            while (channel.isClosed() == false) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            channel.disconnect();

            String output = new String(baos.toByteArray(), CharsetConstants.UTF_8);

            if (checkExitCode && channel.getExitStatus() != 0) {
                throw new SSHException(output);
            }

            return output;
        } catch ( JSchException je ){
            throw new SSHException( "SSH Failed (" + host + ")\n" + command , je );
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private static Session createSession( String username, String privateKeyFile, String host ) throws JSchException {
        JSch jsch = new JSch();

        if (privateKeyFile != null) {
            jsch.addIdentity(privateKeyFile);
        } else {
            findAndAddIdentities(jsch);
        }

        Session session = jsch.getSession( username , host , 22 );
        session.setUserInfo( new MyUserInfo( ) );
        session.setTimeout( CONNECT_TIMEOUT );
        session.connect( CONNECT_TIMEOUT );
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
                Log.error(SSH.class, "Couldn't load SSH identity from file: " + path, e);
            }
        }
    }

    public static void main(String[] jargs) throws SSHException {
        System.out.println();

        Args args = new Args(jargs);
        String user = args.get("user", "localadmin");
        String key = args.get("key");
        String host = args.get("host");
        String cmd = args.get("cmd");
        boolean checkExitCode = args.hasFlag("check");

        String out = sendCommand(user, key, host, cmd, checkExitCode);
        System.out.println(out);
    }
}
