package com.shopwiki.xzcute;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * The only thing special about this is that it treats a call to offer() like it is a call to put().
 *
 * It is the default workQueue for EnhancedThreadPoolExecutorBuilder. 
 *
 * @owner jdickinson
 */
public class EnhancedLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

    private static final long serialVersionUID = 5961764225230396742L;

    public EnhancedLinkedBlockingQueue(int capacity) {
        super(capacity);
    }

    @Override
    public boolean offer(E elem) {
        try {
            super.put(elem);
        } catch (InterruptedException e) {
            throw new RuntimeException("This should really never happen.", e);
        }
        return true;
    }
}
