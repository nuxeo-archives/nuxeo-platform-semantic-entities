/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
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
