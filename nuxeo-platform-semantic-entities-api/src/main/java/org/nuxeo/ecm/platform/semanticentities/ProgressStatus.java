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
