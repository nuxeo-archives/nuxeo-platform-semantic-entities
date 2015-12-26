/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities;

/**
 * Simple data transfer object to report on the state of the analysis process.
 */
public class ProgressStatus {

    public static final String STATUS_ANALYSIS_QUEUED = "status.semantic.analysisQueued";

    public static final String STATUS_ANALYSIS_PENDING = "status.semantic.analysisPending";

    public static final String STATUS_LINKING_QUEUED = "status.semantic.linkingQueued";

    public static final String STATUS_LINKING_PENDING = "status.semantic.linkingPending";

    public final String message;

    public final int positionInQueue;

    public final int queueSize;

    public ProgressStatus(String message) {
        this.message = message;
        positionInQueue = 0;
        queueSize = 0;
    }

    public ProgressStatus(String message, int positionInQueue, int queueSize) {
        this.message = message;
        this.positionInQueue = positionInQueue;
        this.queueSize = queueSize;
    }

    public String getMessage() {
        return message;
    }

    public int getPositionInQueue() {
        return positionInQueue;
    }

    public int getQueueSize() {
        return queueSize;
    }

}
