package com.shopwiki.xzcute;

import java.util.concurrent.*;

import com.shopwiki.xzcute.DaemonThreadFactory;

/**
 * @owner rstewart
 */
public class DaemonScheduledExecutor extends ScheduledThreadPoolExecutor {

    public DaemonScheduledExecutor(int corePoolSize) {
        super(corePoolSize, DaemonThreadFactory.getInstance());
    }

    public DaemonScheduledExecutor(int corePoolSize, String name) {
        super(corePoolSize, DaemonThreadFactory.getInstance(name, corePoolSize > 1));
    }
}
