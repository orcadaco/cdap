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

import co.cask.cdap.api.retry.RetryableException;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.internal.AppFabricTestHelper;
import co.cask.cdap.internal.bootstrap.executor.BaseStepExecutor;
import co.cask.cdap.internal.bootstrap.executor.BootstrapStepExecutor;
import co.cask.cdap.internal.bootstrap.executor.EmptyArguments;
import co.cask.cdap.proto.bootstrap.BootstrapResult;
import co.cask.cdap.proto.bootstrap.BootstrapStepResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.multibindings.MapBinder;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Tests for the {@link BootstrapService}.
 */
public class BootstrapServiceTest {
  @ClassRule
  public static final TemporaryFolder TMP_FOLDER = new TemporaryFolder();
  private static final Gson GSON = new Gson();

  private static Map<BootstrapStep.Type, FakeExecutor> fakeExecutors;
  private static BootstrapService bootstrapService;
  private static BootstrapConfig bootstrapConfig;

  @BeforeClass
  public static void setupClass() throws IOException {
    File bootstrapFile = TMP_FOLDER.newFile("bootstrap.json");
    CConfiguration cConf = CConfiguration.create();
    cConf.set(Constants.BOOTSTRAP_FILE, bootstrapFile.getAbsolutePath());

    // add a step for each type and write the contents to the bootstrap file
    // also create fake step executors for each step that will let us control when they succeed and fail
    fakeExecutors = new LinkedHashMap<>();
    List<BootstrapStep> steps = new ArrayList<>(BootstrapStep.Type.values().length);
    int i = 0;
    for (BootstrapStep.Type type : BootstrapStep.Type.values()) {
      // steps will alternate between run always and run once
      BootstrapStep.RunCondition runCondition = getRunCondition(i);
      steps.add(new BootstrapStep("step-" + i, type, runCondition, new JsonObject()));
      fakeExecutors.put(type, new FakeExecutor());
      i++;
    }
    bootstrapConfig = new BootstrapConfig(steps);
    try (Writer writer = new FileWriter(bootstrapFile)) {
      writer.write(GSON.toJson(bootstrapConfig));
    }


    Injector injector = AppFabricTestHelper.getInjector(cConf, new AbstractModule() {
      @Override
      protected void configure() {
        MapBinder<BootstrapStep.Type, BootstrapStepExecutor> mapBinder = MapBinder.newMapBinder(
          binder(), BootstrapStep.Type.class, BootstrapStepExecutor.class);
        for (Map.Entry<BootstrapStep.Type, FakeExecutor> entry : fakeExecutors.entrySet()) {
          mapBinder.addBinding(entry.getKey()).toInstance(entry.getValue());
        }
      }
    });
    bootstrapService = injector.getInstance(BootstrapService.class);
    bootstrapService.reload();
  }

  @After
  public void cleanupTest() {
    bootstrapService.clearBootstrapState();
    for (FakeExecutor fakeExecutor : fakeExecutors.values()) {
      fakeExecutor.reset();
    }
  }

  @Test
  public void testAllSuccess() {
    BootstrapResult result = bootstrapService.bootstrap();

    List<BootstrapStepResult> expectedStepResults = new ArrayList<>();
    for (BootstrapStep step : bootstrapConfig.getSteps()) {
      expectedStepResults.add(new BootstrapStepResult(step.getLabel(), BootstrapStepResult.Status.SUCCEEDED));
    }
    BootstrapResult expected = new BootstrapResult(expectedStepResults);
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testRunCondition() {
    bootstrapService.bootstrap();
    BootstrapResult result =
      bootstrapService.bootstrap(step -> step.getRunCondition() == BootstrapStep.RunCondition.ONCE);

    int i = 0;
    for (BootstrapStepResult stepResult : result.getSteps()) {
      BootstrapStep.RunCondition runCondition = getRunCondition(i);
      BootstrapStepResult.Status expected = runCondition == BootstrapStep.RunCondition.ALWAYS ?
        BootstrapStepResult.Status.SUCCEEDED : BootstrapStepResult.Status.SKIPPED;
      Assert.assertEquals(expected, stepResult.getStatus());
      i++;
    }
  }

  @Test
  public void testRetries() {
    for (FakeExecutor fakeExecutor : fakeExecutors.values()) {
      fakeExecutor.setNumRetryableFailures(1);
    }
    BootstrapResult result = bootstrapService.bootstrap();

    List<BootstrapStepResult> expectedStepResults = new ArrayList<>();
    for (BootstrapStep step : bootstrapConfig.getSteps()) {
      expectedStepResults.add(new BootstrapStepResult(step.getLabel(), BootstrapStepResult.Status.SUCCEEDED));
    }
    BootstrapResult expected = new BootstrapResult(expectedStepResults);
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testContinuesAfterFailures() {
    int i = 0;
    for (FakeExecutor fakeExecutor : fakeExecutors.values()) {
      if (i % 2 == 0) {
        fakeExecutor.setShouldFail(true);
      }
      i++;
    }
    BootstrapResult result = bootstrapService.bootstrap();

    List<BootstrapStepResult> expectedStepResults = new ArrayList<>();
    i = 0;
    for (BootstrapStep step : bootstrapConfig.getSteps()) {
      BootstrapStepResult.Status expectedStatus = i % 2 == 0 ?
        BootstrapStepResult.Status.FAILED : BootstrapStepResult.Status.SUCCEEDED;
      expectedStepResults.add(new BootstrapStepResult(step.getLabel(), expectedStatus));
      i++;
    }
    BootstrapResult expected = new BootstrapResult(expectedStepResults);
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testNoConcurrentBootstrap() throws InterruptedException {
    CountDownLatch runningLatch = new CountDownLatch(1);
    CountDownLatch waitingLatch = new CountDownLatch(1);
    FakeExecutor fakeExecutor = fakeExecutors.values().iterator().next();
    fakeExecutor.setLatches(runningLatch, waitingLatch);

    Thread t = new Thread(() -> bootstrapService.bootstrap());
    t.start();
    runningLatch.await();

    try {
      bootstrapService.bootstrap();
      Assert.fail("BootstrapService should not allow concurrent bootstrap operations");
    } catch (IllegalStateException e) {
      // expected
    } finally {
      waitingLatch.countDown();
    }
  }

  private static BootstrapStep.RunCondition getRunCondition(int i) {
    return i % 2 == 0 ? BootstrapStep.RunCondition.ALWAYS : BootstrapStep.RunCondition.ONCE;
  }

  /**
   * A fake executor that can be configured to complete, fail, or fail in a retry-able fashion.
   */
  private static class FakeExecutor extends BaseStepExecutor<EmptyArguments> {
    private int numAttempts;
    private int numRetryableFailures;
    private boolean shouldFail;
    private CountDownLatch runningLatch;
    private CountDownLatch waitingLatch;

    public FakeExecutor() {
      reset();
    }

    private void reset() {
      numAttempts = 0;
      numRetryableFailures = 0;
      shouldFail = false;
      runningLatch = null;
      waitingLatch = null;
    }

    private void setNumRetryableFailures(int numRetryableFailures) {
      this.numRetryableFailures = numRetryableFailures;
    }

    private void setShouldFail(boolean shouldFail) {
      this.shouldFail = shouldFail;
    }

    private void setLatches(CountDownLatch runningLatch, CountDownLatch waitingLatch) {
      this.runningLatch = runningLatch;
      this.waitingLatch = waitingLatch;
    }

    @Override
    public void execute(EmptyArguments arguments) throws Exception {
      try {
        if (shouldFail) {
          throw new Exception();
        }
        if (numRetryableFailures > numAttempts) {
          throw new RetryableException();
        }
        if (runningLatch != null) {
          runningLatch.countDown();
          waitingLatch.await();
        }
      } finally {
        numAttempts++;
      }
    }
  }
}
