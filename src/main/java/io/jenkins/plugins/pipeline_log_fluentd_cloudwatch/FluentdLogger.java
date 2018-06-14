/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.pipeline_log_fluentd_cloudwatch;

import hudson.AbortException;
import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.fluentd.logger.FluentLogger;

/**
 * TODO
 */
final class FluentdLogger implements BuildListener {

    private static final long serialVersionUID = 1;

    private final String tag;
    private final String buildId;
    private final String host;
    private final int port;
    private transient PrintStream logger;

    FluentdLogger(String fullName, int number) throws IOException {
        tag = fullName;
        buildId = Integer.toString(number);
        host = System.getenv("FLUENTD_HOST");
        if (host == null) {
            throw new AbortException("You must specify the environment variable FLUENTD_HOST");
        }
        String portS = System.getenv("FLUENTD_PORT");
        port = portS == null ? 24224 : Integer.parseInt(portS);
    }

    @Override
    public PrintStream getLogger() {
        if (logger == null) {
            try {
                logger = new PrintStream(new FluentdOutputStream(), true, "UTF-8");
            } catch (UnsupportedEncodingException x) {
                throw new AssertionError(x);
            }
        }
        return logger;
    }

    private class FluentdOutputStream extends LineTransformationOutputStream {
        
        private final FluentLogger logger;

        FluentdOutputStream() {
            logger = FluentLogger.getLogger(tag, host, port);
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            int eol = len;
            while (eol > 0) {
                byte c = b[eol - 1];
                if (c == '\n' || c == '\r') {
                    eol--;
                } else {
                    break;
                }
            }
            String line = new String(b, 0, eol, StandardCharsets.UTF_8);
            String nodeId, message;
            int sep = line.indexOf(PipelineBridge.NODE_ID_SEP);
            if (sep == -1) {
                nodeId = null;
                message = line;
            } else {
                nodeId = line.substring(0, sep);
                message = line.substring(sep + PipelineBridge.NODE_ID_SEP.length());
            }
            Map<String, Object> data = new HashMap<>();
            data.put("buildId", buildId);
            if (nodeId != null) {
                data.put("nodeId", nodeId);
            }
            data.put("message", message);
            logger.log(tag, data);
        }

    }

}
