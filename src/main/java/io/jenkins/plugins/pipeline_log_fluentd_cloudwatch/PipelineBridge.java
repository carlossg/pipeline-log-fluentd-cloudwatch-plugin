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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.console.AnnotatedLargeText;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageFactory;

/**
 * Binds fluentd and CloudWatch to Pipeline logs.
 */
@Extension
public final class PipelineBridge implements LogStorageFactory {

    /**
     * Map from {@code fullName#id} of builds to the last event timestamp known to have been sent to fluentd.
     * When serving the log for that build, if the last observed timestamp is older, we wait until CloudWatch catches up.
     * Once it does, we remove the entry since we no longer need to catch up further.
     * TODO consider persisting this mapping.
     * TODO alternately, could be part of the {@link LogStorage} state, if we take care to keep that {@code transient} for the {@link FluentdLogger}.
     */
    private final Map<String, Long> lastRecordedTimestamps = new HashMap<>();

    @Override
    public LogStorage forBuild(FlowExecutionOwner b) {
        String url;
        try {
            url = b.getUrl();
        } catch (IOException x) {
            return new BrokenLogStorage(x);
        }
        Matcher m = Pattern.compile("(.+)/([^/]+)/").matcher(url);
        if (!m.matches()) {
            return new BrokenLogStorage(new IllegalArgumentException(url + " is not in expected format"));
        }
        final String logStreamName = m.group(1);
        final String buildId = m.group(2);
        return new LogStorage() {
            @Override
            public BuildListener overallListener() throws IOException, InterruptedException {
                return new FluentdLogger(logStreamName, buildId, null);
            }
            @Override
            public TaskListener nodeListener(FlowNode node) throws IOException, InterruptedException {
                return new FluentdLogger(logStreamName, buildId, node.getId());
            }
            @Override
            public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(FlowExecutionOwner.Executable build, boolean complete) {
                try {
                    return new CloudWatchRetriever(logStreamName, buildId).overallLog(build, complete);
                } catch (Exception x) {
                    return new BrokenLogStorage(x).overallLog(build, complete);
                }
            }
            @Override
            public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete) {
                try {
                    return new CloudWatchRetriever(logStreamName, buildId).stepLog(node, complete);
                } catch (Exception x) {
                    return new BrokenLogStorage(x).stepLog(node, complete);
                }
            }
        };
    }

    /**
     * Called when we are delivering an event to fluentd.
     */
    void eventSent(String fullName, String id, long timestamp) {
        String key = fullName + "#" + id;
        synchronized (lastRecordedTimestamps) {
            Long previous = lastRecordedTimestamps.get(key);
            if (previous == null || previous < timestamp) {
                lastRecordedTimestamps.put(key, timestamp);
            }
        }
    }
    
    /**
     * Called when looking in CloudWatch for the last-delivered event.
     * @return 0 if there is no record
     */
    long latestEvent(String fullName, String id) {
        String key = fullName + "#" + id;
        synchronized (lastRecordedTimestamps) {
            Long timestamp = lastRecordedTimestamps.get(key);
            return timestamp != null ? timestamp : 0;
        }
    }

    /**
     * Called when we have successfully observed an event in CloudWatch.
     * @param timestamp as in return value of {@link #latestEvent}
     */
    void caughtUp(String fullName, String id, long timestamp) {
        String key = fullName + "#" + id;
        synchronized (lastRecordedTimestamps) {
            Long previous = lastRecordedTimestamps.get(key);
            if (previous != null && previous == timestamp) {
                lastRecordedTimestamps.remove(key);
            }
        }
    }

    static PipelineBridge get() {
        return ExtensionList.lookupSingleton(PipelineBridge.class);
    }

}
