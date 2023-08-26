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
package org.apache.spark.status.api.v1

import java.util.{List => JList}
import javax.ws.rs.{DefaultValue, GET, Produces, QueryParam}
import javax.ws.rs.core.MediaType


@Produces(Array(MediaType.APPLICATION_JSON))
private[v1] class ConfigurationListResource extends ApiRequestContext {

  @GET
  def appList(
      @QueryParam("status") status: JList[ApplicationStatus],
      @DefaultValue("2010-01-01") @QueryParam("minDate") minDate: SimpleDateParam,
      @DefaultValue("3000-01-01") @QueryParam("maxDate") maxDate: SimpleDateParam,
      @DefaultValue("2010-01-01") @QueryParam("minEndDate") minEndDate: SimpleDateParam,
      @DefaultValue("3000-01-01") @QueryParam("maxEndDate") maxEndDate: SimpleDateParam,
      @QueryParam("limit") limit: Integer)
  : Iterator[ConfigurationInfo] = {

    val numApps = Option(limit).map(_.toInt).getOrElse(Integer.MAX_VALUE)
    val includeCompleted = status.isEmpty || status.contains(ApplicationStatus.COMPLETED)
    val includeRunning = status.isEmpty || status.contains(ApplicationStatus.RUNNING)

    uiRoot.getApplicationInfoList.filter { app =>
      val anyRunning = app.attempts.isEmpty || !app.attempts.head.completed
      // if any attempt is still running, we consider the app to also still be running;
      // keep the app if *any* attempts fall in the right time window
      ((!anyRunning && includeCompleted) || (anyRunning && includeRunning)) &&
      app.attempts.exists { attempt =>
        isAttemptInRange(attempt, minDate, maxDate, minEndDate, maxEndDate, anyRunning)
      }
    }.take(numApps)
      .map {
        app =>
          withUI(app.id, None) { ui =>
            val environmentInfo: ApplicationEnvironmentInfo = ui.store.environmentInfo()
            val appConfigs = ApplicationConfigs(
              totalCores = Some(1),
              coresPerExecutor = Some(1),
              memoryPerExecutor = Some(1),
              totalMemory = Some(1),
              memoryPerCoreGb = Some(1)
            )

            ConfigurationInfo(
              id = app.id,
              name = app.name,
              coresGranted = app.coresGranted,
              maxCores = app.maxCores,
              coresPerExecutor = app.coresPerExecutor,
              memoryPerExecutorMB = app.memoryPerExecutorMB,
              attempts = app.attempts,
              applicationConfigs = appConfigs,
              customConfigs = Map(("custom.runId", "run-0102"), ("custom.gitbranch", "master"),
                ("custom.owner", "hamza")),
              sparkProperties = environmentInfo.sparkProperties
            )
          }
      }
  }

  private def getConfig(envInfo: ApplicationEnvironmentInfo, keyword: String)
  : Option[String] = {
    envInfo.sparkProperties
      .find{case (_, v) => v == keyword }
      .map(_._2)
  }
}
