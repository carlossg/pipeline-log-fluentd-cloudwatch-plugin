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
import hudson.model.BuildListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.console.PipelineLogFile;

/**
 * Binds fluentd and CloudWatch to Pipeline logs.
 */
@Extension
public final class PipelineBridge extends PipelineLogFile {

    /**
     * Map from {@code fullName#id} of builds to the last event timestamp known to have been sent to fluentd.
     * When serving the log for that build, if the last observed timestamp is older, we wait until CloudWatch catches up.
     * Once it does, we remove the entry since we no longer need to catch up further.
     * TODO consider persisting this mapping.
     */
    private final Map<String, Long> lastRecordedTimestamps = new HashMap<>();

    @Override
    protected BuildListener listenerFor(WorkflowRun b) throws IOException, InterruptedException {
        return new FluentdLogger(b.getParent().getFullName(), b.getId());
    }

    @Override
    protected InputStream logFor(WorkflowRun b, long start, boolean complete) throws IOException {
        return new CloudWatchRetriever(b.getParent().getFullName(), b.getId(), complete).open(start);
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
