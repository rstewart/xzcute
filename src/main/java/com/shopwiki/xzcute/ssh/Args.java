package com.shopwiki.xzcute.sshexec;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Copied & modified from shopwiki repo.
 *
 * -X is a flag
 * -X=foo sets a flag value
 */
public class Args {

    private final boolean _ignoreCase;
    private final Map<String, String> _flagToValue = Maps.newHashMap();
    private final List<String> _args = Lists.newArrayList();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Flag Values: ").append(_flagToValue).append("\n");
        sb.append("Args: ").append(_args).append("\n");
        return sb.toString();
    }

    public Args(String[] args) {
        this(args, false);
    }

    public Args(String[] args, boolean ignoreCase) {

        _ignoreCase = ignoreCase;

        if (args == null) {
            args = new String[0];
        }

        for (int i = 0; i < args.length; i++) {

            String arg = args[i];

            if (arg == null) {
                continue;
            }

            arg = arg.trim();

            if (arg.isEmpty()) {
                continue;
            }

            if (! arg.startsWith("-") || arg.length() <= 1) {
                _args.add(arg);
                continue;
            }

            String entry = arg.substring(1);
            int eqIndx = entry.indexOf('=');

            if (eqIndx < 0) {
                set(entry, null);
                continue;
            }

            String flag = entry.substring(0, eqIndx);
            String val = entry.substring(eqIndx + 1);

            if (val.startsWith("\"")) {
                String oldVal = val;
                int oldI = i;

                while (i + 1 < args.length) {
                    val += " " + args[++i];
                    if (val.endsWith("\"")) {
                        break;
                    }
                }

                if (val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1); // trim off the quotes
                } else {
                    val = oldVal;
                    i = oldI;
                }
            }

            set(flag, val);
        }
    }

    public void set(String name, String value) {
        if (_ignoreCase) {
            name = name.toLowerCase();
        }
        _flagToValue.put(name, value);
    }

    public Set<String> getFlags() {
        return _flagToValue.keySet();
    }

    public boolean hasFlag(String flag) {
        return get(flag) != null;
    }

    public String get(String flag) {
        if (flag == null) {
            return null;
        }

        flag = flag.trim();

        if (flag.charAt(0) == '-') {
            flag = flag.substring(1);
        }

        if (flag.isEmpty()) {
            return null;
        }

        if (_ignoreCase) {
            flag = flag.toLowerCase();
        }

        return _flagToValue.get(flag);
    }

    public List<String> getArgs() {
        return _args;
    }
}
