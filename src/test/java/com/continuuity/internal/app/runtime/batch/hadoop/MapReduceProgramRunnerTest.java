package com.continuuity.internal.app.runtime.batch.hadoop;

import com.continuuity.TestHelper;
import com.continuuity.api.Application;
import com.continuuity.api.common.Bytes;
import com.continuuity.api.data.DataSet;
import com.continuuity.api.data.OperationException;
import com.continuuity.api.data.dataset.KeyValueTable;
import com.continuuity.api.data.dataset.SimpleTimeseriesTable;
import com.continuuity.api.data.dataset.TimeseriesTable;
import com.continuuity.app.DefaultId;
import com.continuuity.app.guice.BigMamaModule;
import com.continuuity.app.program.Program;
import com.continuuity.app.runtime.ProgramController;
import com.continuuity.app.runtime.ProgramRunner;
import com.continuuity.archive.JarFinder;
import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.conf.Constants;
import com.continuuity.data.DataFabricImpl;
import com.continuuity.data.dataset.DataSetInstantiator;
import com.continuuity.data.operation.OperationContext;
import com.continuuity.data.operation.executor.OperationExecutor;
import com.continuuity.data.operation.executor.SynchronousTransactionAgent;
import com.continuuity.data.operation.executor.TransactionProxy;
import com.continuuity.data.runtime.DataFabricLevelDBModule;
import com.continuuity.discovery.DiscoveryService;
import com.continuuity.filesystem.Location;
import com.continuuity.internal.app.deploy.pipeline.ApplicationWithPrograms;
import com.continuuity.internal.app.runtime.DefaultProgramOptions;
import com.continuuity.internal.app.runtime.ProgramRunnerFactory;
import com.continuuity.internal.filesystem.LocalLocationFactory;
import com.continuuity.runtime.FlowTest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class MapReduceProgramRunnerTest {
  private static final Logger LOG = LoggerFactory.getLogger(FlowTest.class);
  private static Injector injector;
  private static CConfiguration configuration;

  @BeforeClass
  public static void beforeClass() {
    configuration = CConfiguration.create();
    configuration.set(Constants.CFG_APP_FABRIC_TEMP_DIR, "/tmp/app/temp");
    configuration.set(Constants.CFG_APP_FABRIC_OUTPUT_DIR, "/tmp/app/archive" + UUID.randomUUID());

    injector = Guice.createInjector(new DataFabricLevelDBModule(configuration),
                                             new BigMamaModule(configuration));
  }

  @Before
  public void before() throws Exception {
    injector.getInstance(DiscoveryService.class).startAndWait();
    // todo
//    injector.getInstance(MapReduceRuntimeService.class).startUp();
  }

  @After
  public void after() {
    // todo
//    injector.getInstance(MapReduceRuntimeService.class).shutDown();
    injector.getInstance(DiscoveryService.class).stop();
  }

  @Test
  public void testWordCount() throws Exception {
    final ApplicationWithPrograms app = deployApp(AppWithMapReduceJob.class);

    OperationExecutor opex = injector.getInstance(OperationExecutor.class);
    OperationContext opCtx = new OperationContext(DefaultId.ACCOUNT.getId(),
                                                  app.getAppSpecLoc().getSpecification().getName());

    String inputPath = createInput();
    File outputDir = new File(FileUtils.getTempDirectory().getPath() + "/out_" + System.currentTimeMillis());
    outputDir.deleteOnExit();

    KeyValueTable jobConfigTable = (KeyValueTable) getTable(opex, opCtx, "jobConfig");
    jobConfigTable.write(tb("inputPath"), tb(inputPath));
    jobConfigTable.write(tb("outputPath"), tb(outputDir.getPath()));

    runProgram(app, AppWithMapReduceJob.ClassicWordCountJob.class);

    File outputFile = outputDir.listFiles()[0];
    int lines = 0;
    BufferedReader reader = new BufferedReader(new FileReader(outputFile));
    try {
      while (true) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        lines++;
      }
    } finally {
      reader.close();
    }
    // dummy check that output file is not empty
    Assert.assertTrue(lines > 0);
  }

  @Test
  public void testTimeSeriesRecordsCount() throws Exception {
    final ApplicationWithPrograms app = deployApp(AppWithMapReduceJob.class);

    OperationExecutor opex = injector.getInstance(OperationExecutor.class);
    OperationContext opCtx = new OperationContext(DefaultId.ACCOUNT.getId(),
                                                  app.getAppSpecLoc().getSpecification().getName());

    TimeseriesTable table = (TimeseriesTable) getTable(opex, opCtx, "timeSeries");

    fillTestInputData(table);

    Thread.sleep(2);

    long start = System.currentTimeMillis();
    runProgram(app, AppWithMapReduceJob.AggregateTimeseriesByTagJob.class);
    long stop = System.currentTimeMillis();

    Map<String, Long> expected = Maps.newHashMap();
    // note: not all records add to the sum since filter by tag="tag1" and ts={1..3} is used
    expected.put("tag1", 18L);
    expected.put("tag2", 3L);
    expected.put("tag3", 18L);
    List<TimeseriesTable.Entry> agg = table.read(AggregateMetricsByTag.BY_TAGS, start, stop);
    Assert.assertEquals(expected.size(), agg.size());
    for (TimeseriesTable.Entry entry : agg) {
      String tag = Bytes.toString(entry.getTags()[0]);
      Assert.assertEquals((long) expected.get(tag), Bytes.toLong(entry.getValue()));
    }
  }

  private void fillTestInputData(TimeseriesTable table) throws OperationException {
    byte[] metric1 = Bytes.toBytes("metric");
    byte[] metric2 = Bytes.toBytes("metric2");
    byte[] tag1 = Bytes.toBytes("tag1");
    byte[] tag2 = Bytes.toBytes("tag2");
    byte[] tag3 = Bytes.toBytes("tag3");
    // m1e1 = metric: 1, entity: 1
    SimpleTimeseriesTable.Entry m1e1 =
      new SimpleTimeseriesTable.Entry(metric1, Bytes.toBytes(3L), 1, tag3, tag2, tag1);
    table.write(m1e1);
    SimpleTimeseriesTable.Entry m1e2 =
      new SimpleTimeseriesTable.Entry(metric1, Bytes.toBytes(10L), 2, tag2, tag3);
    table.write(m1e2);
    SimpleTimeseriesTable.Entry m1e3 =
      new SimpleTimeseriesTable.Entry(metric1, Bytes.toBytes(15L), 3, tag1, tag3);
    table.write(m1e3);
    SimpleTimeseriesTable.Entry m1e4 =
      new SimpleTimeseriesTable.Entry(metric1, Bytes.toBytes(23L), 4, tag2);
    table.write(m1e4);

    SimpleTimeseriesTable.Entry m2e1 =
      new SimpleTimeseriesTable.Entry(metric2, Bytes.toBytes(4L), 3, tag1, tag3);
    table.write(m2e1);
  }

  private void runProgram(ApplicationWithPrograms app, Class<?> programClass) throws Exception {
    ProgramRunnerFactory runnerFactory = injector.getInstance(ProgramRunnerFactory.class);
    final Program program = getProgram(app, programClass);
    ProgramRunner runner = runnerFactory.create(ProgramRunnerFactory.Type.valueOf(program.getProcessorType().name()));

    ProgramController controller = runner.run(program, new DefaultProgramOptions(program));
    while (controller.getState() == ProgramController.State.ALIVE) {
      TimeUnit.SECONDS.sleep(1);
    }
  }

  private Program getProgram(ApplicationWithPrograms app, Class<?> programClass) throws ClassNotFoundException {
    for (Program p : app.getPrograms()) {
      if (programClass.getCanonicalName().equals(p.getMainClass().getCanonicalName())) {
        return p;
      }
    }
    return null;
  }

  private ApplicationWithPrograms deployApp(Class<? extends Application> appClass) throws Exception {
    LocalLocationFactory lf = new LocalLocationFactory();

    Location deployedJar = lf.create(
      JarFinder.getJar(appClass, TestHelper.getManifestWithMainClass(appClass))
    );
    deployedJar.deleteOnExit();

    ListenableFuture<?> p = TestHelper.getLocalManager(configuration).deploy(DefaultId.ACCOUNT, deployedJar);
    return (ApplicationWithPrograms) p.get();
  }

  private byte[] tb(String val) {
    return Bytes.toBytes(val);
  }

  private String createInput() throws IOException {
    File inputDir = new File(FileUtils.getTempDirectory().getPath() + "/in_" + System.currentTimeMillis());
    inputDir.mkdirs();
    inputDir.deleteOnExit();

    File inputFile = new File(inputDir.getPath() + "/words.txt");
    inputFile.deleteOnExit();
    BufferedWriter writer = new BufferedWriter(new FileWriter(inputFile));
    try {
      writer.write("this text has");
      writer.newLine();
      writer.write("two words text inside");
    } finally {
      writer.close();
    }

    return inputDir.getPath();
  }

  private DataSet getTable(OperationExecutor opex, OperationContext opCtx, String tableName) {
    TransactionProxy proxy = new TransactionProxy();
    proxy.setTransactionAgent(new SynchronousTransactionAgent(opex, opCtx));
    DataSetInstantiator dataSetInstantiator = new DataSetInstantiator(new DataFabricImpl(opex, opCtx), proxy,
                                                                      getClass().getClassLoader());
    dataSetInstantiator.setDataSets(ImmutableList.copyOf(new AppWithMapReduceJob().configure().getDataSets().values()));

    return dataSetInstantiator.getDataSet(tableName);
  }
}
