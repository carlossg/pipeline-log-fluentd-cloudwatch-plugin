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

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.FilterLogEventsRequest;
import com.amazonaws.services.logs.model.FilterLogEventsResult;
import com.amazonaws.services.logs.model.FilteredLogEvent;
import com.google.common.primitives.Ints;
import hudson.AbortException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.ByteArrayOutputStream;

/**
 * Retrieves build logs from CloudWatch.
 */
class CloudWatchRetriever {

    private final String logStreamName;
    private final String buildId;
    private final String logGroupName;
    private final AWSLogs client;

    CloudWatchRetriever(String logStreamName, String buildId) throws IOException {
        this.logStreamName = logStreamName;
        this.buildId = buildId;
        logGroupName = System.getenv("CLOUDWATCH_LOG_GROUP_NAME");
        if (logGroupName == null) {
            throw new AbortException("You must specify the environment variable CLOUDWATCH_LOG_GROUP_NAME");
        }
        client = AWSLogsClientBuilder.defaultClient();
    }

    InputStream open(long start) throws IOException {
        // TODO inefficient; pull lazily if possible
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        String token = null;
        try {
            do {
                FilterLogEventsResult result = client.filterLogEvents(new FilterLogEventsRequest().
                    withNextToken(token).
                    withLogGroupName(logGroupName).
                    withLogStreamNames(logStreamName).
                    withFilterPattern("{$.build = \"" + buildId + "\"}"));
                token = result.getNextToken();
                List<FilteredLogEvent> events = result.getEvents();
                // TODO pending https://github.com/fluent-plugins-nursery/fluent-plugin-cloudwatch-logs/pull/108:
                events.sort(Comparator.comparingLong(e -> JSONObject.fromObject(e.getMessage()).optLong("timestamp", e.getTimestamp())));
                for (FilteredLogEvent event : events) {
                    // TODO perhaps translate event.timestamp to a TimestampNote
                    JSONObject json = JSONObject.fromObject(event.getMessage());
                    assert buildId.equals(json.optString("build"));
                    String nodeId = json.optString("node", null);
                    if (nodeId != null) {
                        w.write(nodeId);
                        w.write(PipelineBridge.NODE_ID_SEP);
                    }
                    w.write(json.getString("message"));
                    w.write('\n');
                }
            } while (token != null);
        } catch (RuntimeException x) { // AWS SDK exceptions of various sorts
            throw new IOException(x);
        }
        w.flush();
        int _start = Ints.checkedCast(start); // dumb but this implementation is not streaming anyway
        return new ByteArrayInputStream(baos.toByteArray(), _start, baos.size() - _start);
    }

}
