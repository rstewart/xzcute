package com.shopwiki.xzcute.sshexec;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

/**
 * @owner Jim
 * @buddy Eliot
 */
public class LineIterator implements Iterator<String>, Iterable<String> {

    private BufferedReader br = null;
    private String nextLine = null;
    private boolean lineWasUsed = true;

    public LineIterator(String filename) {
        this(new File(filename));
    }

    public LineIterator(File file) {
        this(file, null);
    }

    public LineIterator(File file, Charset charset) {
        this(file, charset, 256 * 1024);
    }

    public LineIterator(File file, Charset charset, int bufferSize) {
        this(getInputStream(file), charset, bufferSize);
    }

    public static InputStream getInputStream(File file) {
        try {
            InputStream is = new FileInputStream(file);
            if (IOUtil.isGzip(file)) {
                is = new GZIPInputStream(is);
            }
            return is;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public LineIterator(InputStream inputStream, Charset charset, int bufferSize) {
        Reader r;
        if (charset == null) {
            r = new InputStreamReader(inputStream);
        } else {
            r = new InputStreamReader(inputStream, charset);
        }
        br = new BufferedReader(r, bufferSize);
    }

    public LineIterator(BufferedReader br) {
        this.br = br;
    }

    @Override
    protected void finalize() throws Throwable {
        br.close();
        super.finalize();
    }

    private void queueNextLine() throws IOException {
        if (lineWasUsed) {
            nextLine = br.readLine();
            lineWasUsed = false;
        }
    }

    @Override
    public boolean hasNext() {
        try {
            queueNextLine();
            return nextLine != null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String next() {
        try {
            queueNextLine();
            lineWasUsed = true;
            return nextLine;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<String> iterator() {
        return this;
    }

}
