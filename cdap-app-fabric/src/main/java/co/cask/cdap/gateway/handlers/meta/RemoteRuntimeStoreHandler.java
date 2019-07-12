/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.gateway.handlers.meta;

import co.cask.cdap.api.workflow.WorkflowToken;
import co.cask.cdap.app.store.Store;
import co.cask.cdap.common.internal.remote.MethodArgument;
import co.cask.cdap.internal.app.store.remote.RemoteRuntimeStore;
import co.cask.cdap.proto.WorkflowNodeStateDetail;
import co.cask.cdap.proto.id.ProgramRunId;
import co.cask.http.HttpHandler;
import co.cask.http.HttpResponder;
import com.google.inject.Inject;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Iterator;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

/**
 * The {@link HttpHandler} for handling REST calls from a {@link RemoteRuntimeStore}.
 */
@Path(AbstractRemoteSystemOpsHandler.VERSION + "/execute")
public class RemoteRuntimeStoreHandler extends AbstractRemoteSystemOpsHandler {

  private final Store store;

  @Inject
  RemoteRuntimeStoreHandler(Store store) {
    this.store = store;
  }

  @POST
  @Path("/updateWorkflowToken")
  public void updateWorkflowToken(FullHttpRequest request, HttpResponder responder) throws Exception {
    Iterator<MethodArgument> arguments = parseArguments(request);

    ProgramRunId workflowRunId = deserializeNext(arguments);
    WorkflowToken token = deserializeNext(arguments);
    store.updateWorkflowToken(workflowRunId, token);

    responder.sendStatus(HttpResponseStatus.OK);
  }

  @POST
  @Path("/addWorkflowNodeState")
  public void addWorkflowNodeState(FullHttpRequest request, HttpResponder responder) throws Exception {
    Iterator<MethodArgument> arguments = parseArguments(request);

    ProgramRunId workflowRunId = deserializeNext(arguments);
    WorkflowNodeStateDetail nodeStateDetail = deserializeNext(arguments);
    store.addWorkflowNodeState(workflowRunId, nodeStateDetail);

    responder.sendStatus(HttpResponseStatus.OK);
  }
}
