/*
 * Copyright © 2016 Cask Data, Inc.
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
 */

angular.module(PKG.name + '.feature.hydrator')
  .controller('HydratorPlusPlusListController',
  function(
    $scope,myPipelineApi, $stateParams, GLOBALS,
    mySettings, $state, myHelpers, myWorkFlowApi,
    myWorkersApi, myAppsApi, myAlertOnValium, myLoadingService,
    mySparkApi, $interval, moment, MyPipelineStatusMapper,
    myPipelineCommonApi, PROGRAM_STATUSES) {
    var vm = this;
    vm.$interval = $interval;
    vm.moment = moment;
    vm.pipelineList = [];
    vm.currentVisiblePipelines = [];
    vm.pipelineListLoaded = false;
    vm.MyPipelineStatusMapper = MyPipelineStatusMapper;
    var eventEmitter = window.CaskCommon.ee(window.CaskCommon.ee);
    vm.statusCount = {
      running: 0,
      draft: 0
    };
    vm.PAGE_SIZE = 10;
    vm.GLOBALS = GLOBALS;

    vm.checkForValidPage = (pageNumber) => {
      return (
        !Number.isNaN(pageNumber) &&
        Array.isArray(vm.pipelineList) &&
        Math.floor(vm.pipelineList.length) >= pageNumber
      );
    };

    vm.setCurrentPage = () => {
      let pageNumber = parseInt($stateParams.page, 10);
      if (pageNumber&& vm.checkForValidPage(pageNumber)) {
        vm.currentPage = pageNumber;
      } else {
        vm.currentPage = 1;
      }
      vm.goToPage();
    };

    vm.reloadState = function() {
      $state.reload();
    };
    eventEmitter.on(window.CaskCommon.globalEvents.PUBLISHPIPELINE, vm.reloadState);

    vm.goToPage = function () {
      if (!vm.currentPage) {
        return;
      }
      $stateParams.page = vm.currentPage;
      vm.pipelineListLoaded = true;
      $state.go('hydrator.list', $stateParams, {notify: false});
    };

    vm.deleteDraft = function(draftId) {
      myLoadingService.showLoadingIcon()
      .then(function() {
        let draftName;
        mySettings.get('hydratorDrafts')
          .then(function(res) {
            let draft = myHelpers.objectQuery(res, $stateParams.namespace, draftId);
            if (draft) {
              draftName = draft.name;
              delete res[$stateParams.namespace][draftId];
            }
            return mySettings.set('hydratorDrafts', res);
          })
          .then(
              function success() {
                myLoadingService.hideLoadingIconImmediate();
                $state.reload()
                  .then(function() {
                    myAlertOnValium.show({
                      type: 'success',
                      content: 'Pipeline draft ' + draftName + ' deleted successfully'
                    });
                  });
              },
              function error() {
                myLoadingService.hideLoadingIconImmediate();
                $state.reload()
                  .then(function() {
                    myAlertOnValium.show({
                      type: 'danger',
                      content: 'Pipeline draft ' + draftName + ' delete failed'
                    });
                  });
              });
      });
    };

    vm.deleteApp = function (appId) {
      myLoadingService.showLoadingIcon()
      .then(function() {
        var deleteParams = {
          namespace: $state.params.namespace,
          appId: appId,
          scope: $scope
        };
        return myAppsApi.delete(deleteParams)
          .$promise;
      })
      .then(function success () {
        myLoadingService.hideLoadingIconImmediate();
        $state.reload()
          .then(function() {
            myAlertOnValium.show({
              type: 'success',
              content: 'Pipeline ' + appId + ' deleted successfully'
            });
          });
      }, function error () {
        myLoadingService.hideLoadingIconImmediate();
        $state.reload()
          .then(function() {
            myAlertOnValium.show({
              type: 'danger',
              content:  'Pipeline ' + appId + ' delete failed'
            });
          });
      });
    };

    $scope.$on('$destroy', function() {
      eventEmitter.off(window.CaskCommon.globalEvents.PUBLISHPIPELINE, vm.reloadState);
    });

    vm.getProgramId = (app) => {
      if (app.artifact.name === GLOBALS.etlDataPipeline) {
        return app.artifact.name === GLOBALS.etlDataPipeline ? 'DataPipelineWorkflow' : 'ETLWorkflow';
      }
      if (app.artifact.name === GLOBALS.etlDataStreams) {
        return 'DataStreamsSparkStreaming';
      }
      return null;
    };
    vm.getProgramType = (artifactType) => {
      if (artifactType === GLOBALS.etlDataPipeline) {
        return 'Workflow';
      }
      if (artifactType === GLOBALS.etlDataStreams) {
        return 'Spark';
      }
      return null;
    };
    vm.fetchPipelinesRunsInfo = () => {
      let requestBody = [];
      requestBody = vm.pipelineList.map(app => {
        return {
          appId: app.name,
          programId: vm.getProgramId(app),
          programType: vm.getProgramType(app.artifact.name),
        };
      });
      myPipelineApi.getLatestRuns({
        namespace: $stateParams.namespace
      }, requestBody)
        .$promise
        .then(res => {
          res.forEach(resObj => {
            vm.pipelineList = vm.pipelineList.map(app => {
              let latestRun = resObj.runs[0] || {};
              if (app.name === resObj.appId) {
                app = Object.assign({}, app, {
                  latestRun: latestRun
                });
                vm.setDuration(app, latestRun);
              }
              return app;
            });
          });
          vm.currentVisiblePipelines = vm.pipelineList.slice(
            (vm.currentPage - 1) * vm.PAGE_SIZE,
            ((vm.currentPage - 1) * vm.PAGE_SIZE) + 10
          );
          vm.fetchRunsCount(vm.currentVisiblePipelines);
          vm.fetchWorkflowNextRunTimes();
          vm.updateStatusAppObject();
        });
    };

    vm.getPipelines = () => {
      myPipelineApi.list({
        namespace: $stateParams.namespace
      })
        .$promise
        .then(function success(res) {
          vm.pipelineList = res;
          vm.fetchDrafts()
            .then(() => {
              vm.setCurrentPage();
              vm.fetchPipelinesRunsInfo();
            });
        });
    };

    vm.fetchRunsCount = () => {
      let runsRequestBody = vm.pipelineList.map(pipeline => {
        return {
          appId: pipeline.name,
          programType: vm.getProgramType(pipeline.artifact.name),
          programId: vm.getProgramId(pipeline)
        };
      });
      myPipelineApi.getRunsCount({
        namespace: $stateParams.namespace
      }, runsRequestBody)
        .$promise
        .then((runsCountList) => {
          vm.currentVisiblePipelines = vm.currentVisiblePipelines.map(pipeline => {
            let runsCountObj = _.find(runsCountList, { appId: pipeline.name});
            let numRuns = '--';
            if (typeof runsCountObj !== 'undefined') {
              numRuns = runsCountObj.runCount;
            }
            let newPipelineObj = pipeline;
            newPipelineObj.numRuns = numRuns;
            return newPipelineObj;
          });
        });
    };
    /**
     * Gets the next workflow run times. This must be called after batch objects have been created.
     */
    vm.fetchWorkflowNextRunTimes = () => {
      let batch = [];
      batch = vm.currentVisiblePipelines
        .filter(pipeline => pipeline.artifact.name !== GLOBALS.etlDataStreams)
        .map(pipeline => {
          var workflowId = pipeline.artifact.name === GLOBALS.etlDataPipeline ? 'DataPipelineWorkflow' : 'ETLWorkflow';
          return {
            appId: pipeline.name,
            programType: 'Workflow',
            programId: workflowId
          };
        });
      batch.forEach(function (batchParams) {
        myPipelineCommonApi.nextRunTime({
          namespace: $stateParams.namespace,
          app: batchParams.appId,
          programType: 'workflows',
          programName: batchParams.programId,
          scope: $scope
        })
          .$promise
          .then(function (res) {
          if (res && res.length) {
            vm.currentVisiblePipelines.forEach(function (app) {
              if (app.name === batchParams.appId) {
                app.nextRun = res[0].time;
              }
            });
          }
        });
      });
    };

    vm.setDuration = (app, run = {}) => {
      if (run.starting) {
        let lastRunDuration = run.end ? (run.end - run.starting) : (new Date().getTime() / 1000) - run.starting;
        app.duration = window.CaskCommon.CDAPHelpers.humanReadableDuration(lastRunDuration);
      }
    };

    vm.latestRunExists = (app) => Object.keys(app.latestRun).length !== 0;

    vm.updateStatusAppObject =() => {
      angular.forEach(vm.pipelineList, function (app) {
        if (!vm.latestRunExists(app)) {
          app.displayStatus = vm.MyPipelineStatusMapper.lookupDisplayStatus(PROGRAM_STATUSES.DEPLOYED);
        } else {
          app.displayStatus = vm.MyPipelineStatusMapper.lookupDisplayStatus(app.latestRun.status);
        }
      });
    };

    vm.fetchDrafts = () => {
      return mySettings.get('hydratorDrafts', true)
        .then(function(res) {
          let draftsList = myHelpers.objectQuery(res, $stateParams.namespace);
          if (!angular.isObject(draftsList)) {
            return;
          }
          if (Object.keys(draftsList).length) {
            angular.forEach(res[$stateParams.namespace], function(value, key) {
              vm.statusCount.draft++;
              vm.pipelineList.push({
                isDraft: true,
                name: value.name,
                id: (value.__ui__  && value.__ui__.draftId) ? value.__ui__.draftId : key,
                artifact: value.artifact,
                description: myHelpers.objectQuery(value, 'description'),
                displayStatus: vm.MyPipelineStatusMapper.lookupDisplayStatus('DRAFT'),
                numRuns: 'N/A',
                lastStartTime: 'N/A'
              });
            });
          }
        });
    };


    vm.getPipelines();
  });
