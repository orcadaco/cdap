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
import co.cask.cdap.common.service.Retries;
import co.cask.cdap.common.service.RetryStrategies;
import co.cask.cdap.proto.bootstrap.BootstrapStepResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * Executes a bootstrap step.
 *
 * @param <T> type of arguments required by the bootstrap step
 */
public abstract class BaseStepExecutor<T extends Validatable> implements BootstrapStepExecutor {
  private static final Gson GSON = new Gson();

  @Override
  public BootstrapStepResult execute(String label, JsonObject argumentsObj) {
    T arguments;
    try {
      arguments = GSON.fromJson(argumentsObj, getArgumentsType());
    } catch (JsonParseException e) {
      String msg = String.format("Failed to decode arguments for step '%s'. Reason: %s",
                                 label, e.getMessage());
      return new BootstrapStepResult(label, BootstrapStepResult.Status.FAILED, msg);
    }

    try {
      arguments.validate();
    } catch (IllegalArgumentException e) {
      return new BootstrapStepResult(label, BootstrapStepResult.Status.FAILED, e.getMessage());
    }

    try {
      Retries.runWithRetries(() -> execute(arguments),
                             RetryStrategies.exponentialDelay(200, 5000, TimeUnit.MILLISECONDS));
      return new BootstrapStepResult(label, BootstrapStepResult.Status.SUCCEEDED);
    } catch (Exception e) {
      return new BootstrapStepResult(label, BootstrapStepResult.Status.FAILED, e.getMessage());
    }
  }

  private Class<T> getArgumentsType() {
    Type superclass = getClass().getGenericSuperclass();
    //noinspection unchecked
    return (Class<T>) ((ParameterizedType) superclass).getActualTypeArguments()[0];
  }

  /**
   * Execute the bootstrap step given the specified arguments
   *
   * @param arguments arguments required to execute the bootstrap step
   * @throws RetryableException if execution failed, but may succeed on retry
   * @throws Exception if execution failed in a way that can't be retried
   */
  protected abstract void execute(T arguments) throws Exception;
}
