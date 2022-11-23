/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytedance.bitsail.base.execution;

import com.bytedance.bitsail.common.configuration.BitSailConfiguration;
import com.bytedance.bitsail.common.configuration.ConfigParser;

import lombok.Getter;

import java.util.List;

/**
 * Created 2022/4/21
 */
@Getter
public abstract class ExecutionEnviron implements BaseExecutionEnviron {

  protected BitSailConfiguration globalConfiguration;
  protected BitSailConfiguration commonConfiguration;
  protected List<BitSailConfiguration> readerConfigurations;
  protected List<BitSailConfiguration> writerConfigurations;
  protected Mode mode;

  /**
   * Constructor for execution environment.
   *
   * @param globalConfiguration User defined configurations.
   * @param mode                Indicate the job type.
   */
  @Override
  public void configure(Mode mode, BitSailConfiguration globalConfiguration) {
    this.globalConfiguration = globalConfiguration;
    this.commonConfiguration = ConfigParser.getSysCommonConf(globalConfiguration);
    this.readerConfigurations = ConfigParser.getInputConfList(globalConfiguration);
    this.writerConfigurations = ConfigParser.getOutputConfList(globalConfiguration);
    this.mode = mode;
  }

  /**
   * Re-initialize reader and writer configurations from user defined configurations.
   */
  public void refreshConfiguration() {
    this.commonConfiguration = ConfigParser.getCommonConf(commonConfiguration);
    this.readerConfigurations = ConfigParser.getInputConfList(globalConfiguration);
    this.writerConfigurations = ConfigParser.getOutputConfList(globalConfiguration);
  }
}
