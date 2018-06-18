FROM jenkins/jenkins:alpine

ARG INCREMENTALS=https://repo.jenkins-ci.org/incrementals/org/jenkins-ci/plugins/
ARG REF=/usr/share/jenkins/ref/plugins

COPY plugins.txt ${REF}.txt

RUN /usr/local/bin/install-plugins.sh < ${REF}.txt

RUN curl -sSL -o $REF/workflow-api.jpi $INCREMENTALS/workflow/workflow-api/2.28-rc214.c2c17ba172df/workflow-api-2.28-rc214.c2c17ba172df.hpi
RUN curl -sSL -o $REF/workflow-durable-task-step.jpi $INCREMENTALS/workflow/workflow-durable-task-step/2.20-rc333.74dc7c303e6d/workflow-durable-task-step-2.20-rc333.74dc7c303e6d.hpi
RUN curl -sSL -o $REF/workflow-job.jpi $INCREMENTALS/workflow/workflow-job/2.22-rc304.ab259f8d8088/workflow-job-2.22-rc304.ab259f8d8088.hpi
RUN curl -sSL -o $REF/workflow-support.jpi $INCREMENTALS/workflow/workflow-support/2.19-rc257.cd70bcc4c209/workflow-support-2.19-rc257.cd70bcc4c209.hpi
RUN curl -sSL -o $REF/durable-task.jpi $INCREMENTALS/durable-task/1.23-rc127.e219d474cdd6/durable-task-1.23-rc127.e219d474cdd6.hpi

COPY target/pipeline-log-fluentd-cloudwatch.hpi ${REF}/
