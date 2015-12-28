/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     matic
 */
package org.nuxeo.ecm.platform.semanticentities.service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.nuxeo.ecm.platform.semanticentities.AnalysisTask;
import org.nuxeo.ecm.platform.semanticentities.SerializationTask;
import org.nuxeo.ecm.platform.semanticentities.service.SemanticAnalysisServiceImpl.NamedThreadFactory;

/**
 * @author matic
 */
public class SemanticAnalysisExecutor {

    protected BlockingQueue<Runnable> analysisTaskQueue;

    protected ThreadPoolExecutor analysisExecutor;

    protected BlockingQueue<Runnable> serializationTaskQueue;

    protected ThreadPoolExecutor serializationExecutor;

    public SemanticAnalysisExecutor() {
        NamedThreadFactory analysisThreadFactory = new NamedThreadFactory("Nuxeo Async Semantic Analysis");
        analysisTaskQueue = new LinkedBlockingQueue<Runnable>();
        analysisExecutor = new ThreadPoolExecutor(4, 8, 5, TimeUnit.MINUTES, analysisTaskQueue, analysisThreadFactory);

        NamedThreadFactory serializationThreadFactory = new NamedThreadFactory(
                "Nuxeo Async Semantic Link Serialization");
        serializationTaskQueue = new LinkedBlockingQueue<Runnable>();
        serializationExecutor = new ThreadPoolExecutor(1, 1, 5, TimeUnit.MINUTES, serializationTaskQueue,
                serializationThreadFactory);
    }

    public void execute(SerializationTask task) {
        while (serializationTaskQueue.remove(task)) {
            // remove duplicates to only link to the latest version
        }
        serializationExecutor.execute(task);
    }

    public boolean shutdownNow() {
        analysisExecutor.shutdownNow();
        try {
            if (!analysisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        serializationExecutor.shutdownNow();
        try {
            if (!serializationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    public boolean shutdown() {
        analysisExecutor.shutdown();
        try {
            if (!analysisExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        serializationExecutor.shutdown();
        try {
            if (!serializationExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    public void execute(AnalysisTask task) {
        analysisExecutor.execute(task);
    }

}
