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
import hudson.model.BuildListener;
import java.io.IOException;
import java.io.InputStream;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.console.PipelineLogFile;
import org.jenkinsci.plugins.workflow.support.actions.AnnotatedLogAction;

/**
 * Binds fluentd and CloudWatch to Pipeline logs.
 */
@Extension
public class PipelineBridge extends PipelineLogFile {

    /** TODO same as {@link AnnotatedLogAction#NODE_ID_SEP}; TBD whether that should be a proper API and where */
    static final String NODE_ID_SEP = "¦";

    @Override
    protected BuildListener listenerFor(WorkflowRun b) throws IOException, InterruptedException {
        return new FluentdLogger(b.getParent().getFullName(), b.getId());
    }

    @Override
    protected InputStream logFor(WorkflowRun b, long start) throws IOException {
        return new CloudWatchRetriever(b.getParent().getFullName(), b.getId()).open(start);
    }

}
