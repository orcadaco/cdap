/*
 * Copyright © 2018 Cask Data, Inc.
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
 *
 */

package co.cask.cdap.internal.bootstrap.executor;

import co.cask.cdap.api.retry.RetryableException;
import co.cask.cdap.common.NamespaceAlreadyExistsException;
import co.cask.cdap.common.namespace.NamespaceAdmin;
import co.cask.cdap.internal.profile.ProfileService;
import co.cask.cdap.proto.EntityScope;
import co.cask.cdap.proto.NamespaceMeta;
import co.cask.cdap.proto.id.NamespaceId;
import co.cask.cdap.proto.id.ProfileId;
import co.cask.cdap.proto.profile.Profile;
import co.cask.cdap.proto.profile.ProfileCreateRequest;
import co.cask.cdap.proto.provisioner.ProvisionerInfo;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileAlreadyExistsException;

/**
 * Creates the default namespace if it doesn't exist.
 */
public class DefaultNamespaceCreator extends BaseStepExecutor<EmptyArguments> {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultNamespaceCreator.class);
  private final NamespaceAdmin namespaceAdmin;

  @Inject
  DefaultNamespaceCreator(NamespaceAdmin namespaceAdmin) {
    this.namespaceAdmin = namespaceAdmin;
  }

  @Override
  public void execute(EmptyArguments arguments) throws FileAlreadyExistsException {
    try {
      if (!namespaceAdmin.exists(NamespaceId.DEFAULT)) {
        namespaceAdmin.create(NamespaceMeta.DEFAULT);
        LOG.info("Successfully created namespace '{}'.", NamespaceMeta.DEFAULT);
      }
    } catch (FileAlreadyExistsException e) {
      // avoid retrying if its a FileAlreadyExistsException
      LOG.warn("Got exception while trying to create namespace '{}'.", NamespaceMeta.DEFAULT, e);
      throw e;
    } catch (NamespaceAlreadyExistsException e) {
      // default namespace already exists
    } catch (Exception e) {
      throw new RetryableException(e);
    }
  }
}
