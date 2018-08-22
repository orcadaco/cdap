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

import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.data.DatasetContext;
import co.cask.cdap.api.dataset.DatasetManagementException;
import co.cask.cdap.api.dataset.DatasetProperties;
import co.cask.cdap.api.dataset.table.Table;
import co.cask.cdap.data2.datafabric.dataset.DatasetsUtil;
import co.cask.cdap.data2.dataset2.DatasetFramework;
import co.cask.cdap.data2.dataset2.lib.table.MDSKey;
import co.cask.cdap.data2.dataset2.lib.table.MetadataStoreDataset;
import co.cask.cdap.internal.app.store.AppMetadataStore;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;

/**
 * Fetches and stores bootstrap state.
 */
public class BootstrapDataset {
  private static final MDSKey KEY = new MDSKey.Builder().add("boot").build();
  private final MetadataStoreDataset table;

  public static BootstrapDataset get(DatasetContext datasetContext, DatasetFramework dsFramework) {
    try {
      Table table = DatasetsUtil.getOrCreateDataset(datasetContext, dsFramework, AppMetadataStore.APP_META_INSTANCE_ID,
                                                    Table.class.getName(),
                                                    DatasetProperties.EMPTY);
      return new BootstrapDataset(table);
    } catch (DatasetManagementException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private BootstrapDataset(Table table) {
    this.table = new MetadataStoreDataset(table);
  }

  /**
   * @return whether the CDAP instance is bootstrapped.
   */
  public boolean isBootstrapped() {
    return table.exists(KEY);
  }

  /**
   * Mark the CDAP instance as bootstrapped.
   */
  public void bootstrapped() {
    table.write(KEY, Boolean.TRUE);
  }

  /**
   * Clear bootstrap state. This should only be called in tests.
   */
  @VisibleForTesting
  void clear() {
    table.delete(KEY);
  }
}
