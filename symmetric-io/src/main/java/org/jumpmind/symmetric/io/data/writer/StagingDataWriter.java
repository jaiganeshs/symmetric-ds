/*
 * Licensed to JumpMind Inc under one or more contributor 
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding 
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Lesser General Public License (the
 * "License"); you may not use this file except in compliance
 * with the License. 
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, see           
 * <http://www.gnu.org/licenses/>.
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License. 
 */
package org.jumpmind.symmetric.io.data.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.util.FormatUtils;

public class StagingDataWriter extends AbstractProtocolDataWriter {

    private IStagingManager stagingManager;
    
    private String category;
    
    private Map<Batch, IStagedResource> stagedResources = new HashMap<Batch, IStagedResource>();

    public StagingDataWriter(String sourceNodeId, String category, IStagingManager stagingManager,
            IProtocolDataWriterListener... listeners) {
        this(sourceNodeId, category, stagingManager, toList(listeners));
    }

    public StagingDataWriter(String sourceNodeId, String category, IStagingManager stagingManager,
            List<IProtocolDataWriterListener> listeners) {
        super(sourceNodeId, listeners, false);
        this.category = category;
        this.stagingManager = stagingManager;
    }

    public static List<IProtocolDataWriterListener> toList(IProtocolDataWriterListener... listeners) {
        ArrayList<IProtocolDataWriterListener> list = new ArrayList<IProtocolDataWriterListener>(
                listeners.length);
        for (IProtocolDataWriterListener l : listeners) {
            list.add(l);
        }
        return list;
    }

    @Override
    protected void notifyEndBatch(Batch batch, IProtocolDataWriterListener listener) {
        listener.end(context, batch, getStagedResource(batch));
        stagedResources.remove(batch);
    }

    protected IStagedResource getStagedResource(Batch batch) {
        IStagedResource resource = stagedResources.get(batch);
        if (resource == null) {
            String location = batch.getStagedLocation();
            resource = stagingManager.find(category, location, batch.getBatchId());
            if (resource == null || resource.getState() == State.DONE) {
                log.debug("Creating staged resource for batch {}", batch.getSourceNodeBatchId());
                resource = stagingManager.create(category, location, batch.getBatchId());
            }
            stagedResources.put(batch, resource);
        }
        return resource;
    }

    @Override
    protected void endBatch(Batch batch) {
        IStagedResource resource = getStagedResource(batch);
        resource.close();
        resource.setState(State.READY);
        flushNodeId = true;
        processedTables.clear();
        table = null;        
    }

    @Override
    protected void print(Batch batch, String data) {
        if (log.isDebugEnabled() && data != null) {
            log.debug("Writing staging data: {}", FormatUtils.abbreviateForLogging(data));
        }
        IStagedResource resource = getStagedResource(batch);
        BufferedWriter writer = resource.getWriter();
        try {
            writer.append(data);
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

}
