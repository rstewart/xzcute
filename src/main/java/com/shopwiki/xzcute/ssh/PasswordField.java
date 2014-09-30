package com.shopwiki.xzcute.sshexec;

import java.io.*;

/**
 * Copied from: http://java.sun.com/developer/technicalArticles/Security/pwordmask/
 *
 * Note: The solution makes extensive use of threads,
 * however, if the machine is under heavy load,
 * there is no guarantee that the MaskingThread will run often enough.
 * Please see the rest of the article for an improved version of the code.
 */
public class PasswordField {

    /**
     * The EraserThread class is used by the PasswordField class.
     * This class prompts the user for a password and an instance of EraserThread attempts to mask the input with "*".
     * Note that initially a asterisk (*) will be displayed.
     */
    private static class EraserThread implements Runnable {

        private boolean stop;

        /**
         * @param The prompt displayed to the user
         */
        public EraserThread(String prompt) {
            System.out.print(prompt);
        }

        /**
         * Begin masking...display asterisks (*)
         */
        @Override
        public void run () {
            stop = true;
            while (stop) {
                System.out.print("\010*");
                try {
                    Thread.sleep(1);
                } catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }

        /**
         * Instruct the thread to stop masking
         */
        public void stopMasking() {
            this.stop = false;
        }
    }

    /**
     * @param prompt The prompt to display to the user
     * @return The password as entered by the user
     */
    public static String readPassword (String prompt) {
        EraserThread et = new EraserThread(prompt);
        Thread mask = new Thread(et);
        mask.start();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String password = "";

        try {
            password = in.readLine();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // stop masking
        et.stopMasking();
        // return the password entered by the user
        return password;
    }
}