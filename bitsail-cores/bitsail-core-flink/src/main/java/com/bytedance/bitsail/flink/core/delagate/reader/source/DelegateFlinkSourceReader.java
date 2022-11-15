/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.bytedance.bitsail.flink.core.delagate.reader.source;

import com.bytedance.bitsail.base.dirty.AbstractDirtyCollector;
import com.bytedance.bitsail.base.dirty.DirtyCollectorFactory;
import com.bytedance.bitsail.base.messenger.Messenger;
import com.bytedance.bitsail.base.messenger.MessengerFactory;
import com.bytedance.bitsail.base.messenger.common.MessengerGroup;
import com.bytedance.bitsail.base.messenger.context.SimpleMessengerContext;
import com.bytedance.bitsail.base.metrics.MetricManager;
import com.bytedance.bitsail.base.metrics.manager.BitSailMetricManager;
import com.bytedance.bitsail.base.metrics.manager.CallTracer;
import com.bytedance.bitsail.common.configuration.BitSailConfiguration;
import com.bytedance.bitsail.common.model.ColumnInfo;
import com.bytedance.bitsail.common.option.CommonOptions;
import com.bytedance.bitsail.common.option.ReaderOptions;
import com.bytedance.bitsail.common.typeinfo.TypeInfo;
import com.bytedance.bitsail.common.util.Pair;
import com.bytedance.bitsail.flink.core.delagate.converter.FlinkRowConvertSerializer;
import com.bytedance.bitsail.flink.core.delagate.reader.source.operator.DelegateSourceReaderContext;
import com.bytedance.bitsail.flink.core.runtime.RuntimeContextInjectable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.flink.api.connector.source.ReaderOutput;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.core.io.InputStatus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bytedance.bitsail.base.constants.BaseMetricsNames.RECORD_INVOKE_LATENCY;

public class DelegateFlinkSourceReader<T, SplitT extends com.bytedance.bitsail.base.connector.reader.v1.SourceSplit>
    implements SourceReader<T, DelegateFlinkSourceSplit<SplitT>> {

  private final Function<com.bytedance.bitsail.base.connector.reader.v1.SourceReader.Context,
      com.bytedance.bitsail.base.connector.reader.v1.SourceReader<T, SplitT>> sourceReaderFunction;
  private final DelegateSourceReaderContext sourceReaderContext;
  private final BitSailConfiguration commonConfiguration;
  private final TypeInfo<?>[] sourceTypeInfos;
  private final String[] fieldNames;
  private final List<ColumnInfo> columnInfos;
  private final String readerName;

  private transient com.bytedance.bitsail.base.connector.reader.v1.SourceReader<T, SplitT> sourceReader;
  private transient DelegateSourcePipeline<T> pipeline;
  private transient FlinkRowConvertSerializer flinkRowConvertSerializer;
  private transient CompletableFuture<Void> available;

  private transient Messenger messenger;
  private transient AbstractDirtyCollector dirtyCollector;
  private transient MetricManager metricManager;

  public DelegateFlinkSourceReader(
      Function<com.bytedance.bitsail.base.connector.reader.v1.SourceReader.Context,
          com.bytedance.bitsail.base.connector.reader.v1.SourceReader<T, SplitT>> sourceReaderFunction,
      SourceReaderContext sourceReaderContext,
      String readerName,
      TypeInfo<?>[] typeInfos,
      BitSailConfiguration commonConfiguration,
      BitSailConfiguration readerConfiguration) {
    this.sourceReaderFunction = sourceReaderFunction;
    this.sourceReaderContext = (DelegateSourceReaderContext) sourceReaderContext;
    this.commonConfiguration = commonConfiguration;
    this.readerName = readerName;
    this.sourceTypeInfos = typeInfos;
    this.columnInfos = readerConfiguration.get(ReaderOptions.BaseReaderOptions.COLUMNS);
    this.fieldNames = columnInfos.stream()
        .map(ColumnInfo::getName)
        .collect(Collectors.toList())
        .toArray(new String[] {});

    this.metricManager = new BitSailMetricManager(commonConfiguration,
        "input",
        false,
        ImmutableList.of(
            Pair.newPair("instance", String.valueOf(commonConfiguration.get(CommonOptions.INSTANCE_ID))),
            Pair.newPair("type", this.readerName),
            Pair.newPair("task", String.valueOf(sourceReaderContext.getIndexOfSubtask()))
        )
    );
    prepareSourceReader();
  }

  private void prepareSourceReader() {
    com.bytedance.bitsail.base.connector.reader.v1.SourceReader.Context context =
        new com.bytedance.bitsail.base.connector.reader.v1.SourceReader.Context() {
          @Override
          public TypeInfo<?>[] getTypeInfos() {
            return sourceTypeInfos;
          }

          @Override
          public String[] getFieldNames() {
            return fieldNames;
          }

          @Override
          public int getIndexOfSubtask() {
            return sourceReaderContext.getIndexOfSubtask();
          }

          @Override
          public void sendSplitRequest() {
            sourceReaderContext.sendSplitRequest();
          }
        };
    this.sourceReader = sourceReaderFunction
        .apply(context);
    this.available = new CompletableFuture<>();
    this.flinkRowConvertSerializer = new FlinkRowConvertSerializer(
        sourceTypeInfos,
        columnInfos,
        commonConfiguration);
    SimpleMessengerContext messengerContext = SimpleMessengerContext.builder()
        .messengerGroup(MessengerGroup.READER)
        .instanceId(commonConfiguration.get(CommonOptions.INTERNAL_INSTANCE_ID))
        .build();
    this.dirtyCollector = DirtyCollectorFactory.initDirtyCollector(commonConfiguration, messengerContext);
    this.messenger = MessengerFactory.initMessenger(commonConfiguration, messengerContext);
    if (this.messenger instanceof RuntimeContextInjectable) {
      ((RuntimeContextInjectable) messenger).setRuntimeContext(sourceReaderContext.getRuntimeContext());
    }
  }

  @Override
  public void start() {
    this.metricManager.start();
    this.messenger.open();
    this.sourceReader.start();
  }

  @Override
  public InputStatus pollNext(ReaderOutput<T> output) throws Exception {
    getOrCreateSourcePipeline(output);
    if (sourceReader.hasMoreElements()) {
      pollNext(pipeline);
      return InputStatus.MORE_AVAILABLE;
    }
    return InputStatus.END_OF_INPUT;
  }

  private void pollNext(DelegateSourcePipeline<T> pipeline) throws Exception {
    try (CallTracer ignore = metricManager.recordTimer(RECORD_INVOKE_LATENCY).get()) {
      sourceReader.pollNext(pipeline);
    }
  }

  private void getOrCreateSourcePipeline(ReaderOutput<T> output) {
    if (Objects.isNull(pipeline)) {
      pipeline = new DelegateSourcePipeline<>(
          output,
          flinkRowConvertSerializer,
          metricManager,
          messenger,
          dirtyCollector,
          commonConfiguration);
    }
  }

  @Override
  public List<DelegateFlinkSourceSplit<SplitT>> snapshotState(long checkpointId) {
    List<SplitT> splits = sourceReader.snapshotState(checkpointId);

    List<DelegateFlinkSourceSplit<SplitT>> flinkSourceSplits = Lists.newArrayListWithCapacity(splits.size());
    for (SplitT splitT : splits) {
      flinkSourceSplits.add(new DelegateFlinkSourceSplit<>(splitT));
    }
    return flinkSourceSplits;
  }

  @Override
  public CompletableFuture<Void> isAvailable() {
    //todo understand how available influence lifecycle.
    return available;
  }

  @Override
  public void addSplits(List<DelegateFlinkSourceSplit<SplitT>> splits) {
    List<SplitT> transformSplit = Lists.newArrayListWithCapacity(splits.size());
    for (DelegateFlinkSourceSplit<SplitT> split : splits) {
      transformSplit.add(split.getSourceSplit());
    }
    sourceReader.addSplits(transformSplit);
  }

  @Override
  public void notifyNoMoreSplits() {
    sourceReader.notifyNoMoreSplits();
  }

  @Override
  public void close() throws Exception {
    sourceReader.close();
    messenger.commit();
    dirtyCollector.close();
  }
}
