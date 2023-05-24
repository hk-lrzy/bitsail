/*
 * Copyright 2022-2023 Bytedance Ltd. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytedance.bitsail.common.util;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Map;

public class DateTimeFormatterUtils {

  private static final Map<Integer, DateTimeFormatter> DEFAULT_DATETIME_FORMATTER_MAP;

  static {
    DEFAULT_DATETIME_FORMATTER_MAP = Maps.newHashMap();
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyyMMdd HH:mm:ss".length(),
        DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"));
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyy-MM-dd HH:mm:ss".length(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyy-MM-dd HH:mm:ss.S".length(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"));
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyy-MM-dd HH:mm:ss.SS".length(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS"));
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyy-MM-dd HH:mm:ss.SSS".length(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyy-MM-dd HH:mm:ss.SSSS".length(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSS"));
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyy-MM-dd HH:mm:ss.SSSSS".length(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSS"));
    DEFAULT_DATETIME_FORMATTER_MAP.put(
        "yyyy-MM-dd HH:mm:ss.SSSSSS".length(),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
  }

  public static DateTimeFormatter getFormatter(String str) {
    str = StringUtils.replace(str, "T", " ");
    return DEFAULT_DATETIME_FORMATTER_MAP.get(StringUtils.length(str));
  }

}
