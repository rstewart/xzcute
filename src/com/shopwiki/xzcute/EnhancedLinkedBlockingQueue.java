package com.shopwiki.xzcute;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author jdickinson
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
            // this should really never happen.
            throw new RuntimeException("This should never happen.", e);
        }
        return true;
    }
}