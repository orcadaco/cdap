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

package co.cask.cdap.internal.bootstrap;

import co.cask.cdap.proto.bootstrap.BootstrapResult;
import co.cask.cdap.proto.bootstrap.BootstrapStepResult;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Performs bootstrap steps.
 */
public class BootstrapService extends AbstractIdleService {
  private static final Logger LOG = LoggerFactory.getLogger(BootstrapService.class);
  private final BootstrapConfigProvider bootstrapConfigProvider;
  private BootstrapConfig config;

  @Inject
  BootstrapService(BootstrapConfigProvider bootstrapConfigProvider) {
    this.bootstrapConfigProvider = bootstrapConfigProvider;
    this.config = BootstrapConfig.EMPTY;
  }

  @Override
  protected void startUp() {
    LOG.info("Starting {}", getClass().getSimpleName());
    config = bootstrapConfigProvider.getConfig();
    LOG.info("Started {}", getClass().getSimpleName());
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Stopping {}", getClass().getSimpleName());
    LOG.info("Stopped {}", getClass().getSimpleName());
  }

  /**
   * Execute all steps in the loaded bootstrap config without skipping any of them.
   *
   * @return the result of executing the bootstrap steps.
   * @throws IllegalStateException if bootstrapping is already in progress
   */
  public BootstrapResult bootstrap() {
    return bootstrap(x -> false);
  }

  /**
   * Execute the steps in the loaded bootstrap config.
   *
   * @param shouldSkip predicate that determines whether to skip a step
   * @return the result of executing the bootstrap steps.
   * @throws IllegalStateException if bootstrapping is already in progress
   */
  public BootstrapResult bootstrap(Predicate<BootstrapStep> shouldSkip) {
    List<BootstrapStepResult> results = new ArrayList<>(config.getSteps().size());
    return new BootstrapResult(results);
  }
}
